package io.github.hyungkishin.transentia.infra.rdb.adapter

import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.container.event.TransferEvent
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Outbox 패턴을 위한 송금 이벤트 저장소 구현체
 *
 * 송금 이벤트를 안정적으로 Kafka에 발행하기 위해 Outbox 패턴을 사용한다.
 * 송금 트랜잭션과 같은 DB 트랜잭션 내에서 이벤트를 저장하여 원자성을 보장하고,
 * 별도의 Relay 서버가 이 테이블을 폴링하여 Kafka로 발행한다.
 *
 * 핵심 설계 원칙:
 * - SKIP LOCKED로 동시성 처리하여 여러 Relay 인스턴스 운영 가능
 * - 지수 백오프로 일시적 장애 시 자동 재시도
 * - Stuck SENDING 상태 자동 복구로 서버 재시작 시에도 안정성 보장
 * - 5회 실패 후 DEAD_LETTER 상태로 수동 개입 요구
 */
@Repository
class TransferEventsOutboxJdbcRepository(
    private val jdbc: NamedParameterJdbcTemplate
) : TransferEventsOutboxRepository {

    /**
     * 송금 이벤트를 Outbox 테이블에 저장한다.
     *
     * 송금 트랜잭션과 동일한 DB 트랜잭션 내에서 실행되어 원자성을 보장한다.
     * 중복 저장을 방지하기 위해 event_id를 기준으로 ON CONFLICT DO NOTHING 처리한다.
     *
     * @param row 저장할 송금 이벤트 정보
     */
    override fun save(row: TransferEvent) {
        val sql = """
            INSERT INTO transfer_events(
              event_id, event_version, aggregate_type, aggregate_id, event_type,
              payload, headers, status, attempt_count, created_at, updated_at, next_retry_at
            ) VALUES (:eventId, 1, :aggType, :aggId, :eventType,
                     CAST(:payload AS JSONB), CAST(:headers AS JSONB),
                     'PENDING', 0, now(), now(), now())
            ON CONFLICT (event_id) DO NOTHING
        """.trimIndent()

        jdbc.update(
            sql, mapOf(
                "eventId" to row.eventId,
                "aggType" to row.aggregateType,
                "aggId" to row.aggregateId,
                "eventType" to row.eventType,
                "payload" to row.payload,
                "headers" to row.headers
            )
        )
    }

    /**
     * 처리할 이벤트들을 배치로 조회하고 SENDING 상태로 변경한다.
     *
     * 여러 Relay 인스턴스가 동시에 실행되어도 안전하도록 SKIP LOCKED를 사용한다.
     * Stuck SENDING 상태(10분 이상 진행 중)인 이벤트도 자동으로 복구하여 처리한다.
     * 우선순위는 PENDING > SENDING(Stuck) > FAILED 순으로 처리한다.
     *
     * 처리 흐름:
     * 1. 처리 가능한 이벤트 목록을 조회하고 락을 획득한다
     * 2. 해당 이벤트들을 SENDING 상태로 변경하고 attempt_count를 증가시킨다
     * 3. Stuck SENDING의 경우 attempt_count는 유지한다
     *
     * @param limit 한 번에 처리할 최대 이벤트 수
     * @return 처리할 이벤트 목록
     */
    override fun claimBatch(limit: Int): List<ClaimedRow> {
        val sql = """
          WITH grabbed AS (
            SELECT event_id
            FROM transfer_events
            WHERE (
              status IN ('PENDING', 'FAILED')
              OR (status = 'SENDING' AND updated_at < now() - interval '10 minutes')
            )
              AND next_retry_at <= now()
              AND attempt_count < 5
            ORDER BY 
              CASE 
                WHEN status = 'PENDING' THEN 0 
                WHEN status = 'SENDING' THEN 1
                ELSE 2 
              END,
              created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
          )
          UPDATE transfer_events t
             SET status = 'SENDING',
                 attempt_count = CASE 
                   WHEN t.status = 'SENDING' THEN t.attempt_count
                   ELSE t.attempt_count + 1 
                 END,
                 updated_at = now()
            FROM grabbed g
           WHERE t.event_id = g.event_id
          RETURNING t.event_id, t.aggregate_id, t.payload::text AS payload, 
                   t.headers::text AS headers, t.attempt_count
        """.trimIndent()

        return jdbc.query(sql, mapOf("limit" to limit), claimedRowMapper)
    }

    /**
     * Kafka 발행에 성공한 이벤트들을 PUBLISHED 상태로 변경한다.
     *
     * 이벤트 발행 이력을 추적하기 위해 삭제하지 않고 상태만 변경한다.
     * FDS 분석이나 트러블슈팅 시 발행 이력을 확인할 수 있다.
     *
     * @param ids Kafka 발행에 성공한 이벤트 ID 목록
     */
    override fun markAsPublished(ids: List<Long>) {
        if (ids.isEmpty()) return

        val sql = """
            UPDATE transfer_events 
            SET status = 'PUBLISHED',
                published_at = now(),
                updated_at = now()
            WHERE event_id IN (:ids)
        """.trimIndent()

        jdbc.update(sql, mapOf("ids" to ids))
    }

    /**
     * Kafka 발행에 실패한 이벤트에 백오프 전략을 적용한다.
     *
     * 지수 백오프로 재시도 간격을 늘려가며 일시적 장애에 대응한다.
     * 5회 실패 시 DEAD_LETTER 상태로 변경하여 수동 개입을 요구한다.
     *
     * 백오프 전략:
     * - 1회: 2초 후 재시도
     * - 2회: 4초 후 재시도
     * - 3회: 8초 후 재시도
     * - 4회: 16초 후 재시도
     * - 5회: DEAD_LETTER 상태로 변경
     *
     * @param id 실패한 이벤트 ID
     * @param cause 실패 원인
     * @param backoffMillis 다음 재시도까지 대기할 밀리초
     */
    override fun markFailedWithBackoff(id: Long, cause: String?, backoffMillis: Long) {
        val sql = """
        UPDATE transfer_events
        SET status = CASE 
              WHEN attempt_count >= 5 THEN 'DEAD_LETTER'::transfer_outbox_status
              ELSE 'FAILED'::transfer_outbox_status
            END,
            last_error = :errorMessage,
            updated_at = now(),
            next_retry_at = now() + (:backoffMs || ' milliseconds')::interval
        WHERE event_id = :eventId
    """.trimIndent()

        jdbc.update(
            sql, mapOf(
                "eventId" to id,
                "errorMessage" to (cause ?: "UNKNOWN"),
                "backoffMs" to backoffMillis
            )
        )
    }

    /**
     * DB 조회 결과를 ClaimedRow 객체로 매핑하는 RowMapper
     *
     * JSONB 타입은 ::text로 캐스팅하여 String으로 변환한다.
     */
    private val claimedRowMapper = RowMapper { rs, _ ->
        ClaimedRow(
            eventId = rs.getLong("event_id"),
            aggregateId = rs.getString("aggregate_id"),
            payload = rs.getString("payload"),
            headers = rs.getString("headers"),
            attemptCount = rs.getInt("attempt_count")
        )
    }
}