package io.github.hyungkishin.transentia.relay.partitioning

import io.github.hyungkishin.transentia.container.event.TransferEvent
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant

/**
 * now 파라미터로 시간 기반 로직을 테스트한다.
 *
 * 이제 다음과 같은 테스트가 가능하다:
 * 1. Stuck SENDING 상태 복구 (10분 경과)
 * 2. next_retry_at 기반 재시도
 * 3. 백오프 전략 검증
 * 4. 시간 흐름 시뮬레이션
 */
@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
@ActiveProfiles("test")
@DisplayName("now 파라미터 - 시간 기반 로직 테스트")
class TransferEventsOutboxRepositoryTimeBasedTest {

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    private lateinit var repository: TransferEventsOutboxJdbcRepository

    @BeforeEach
    fun setUp() {
        // 테이블 초기화
        jdbc.update("DELETE FROM transfer_events", emptyMap<String, Any>())
        
        // Repository 초기화 (모든 테스트에서 안정적으로 사용)
        repository = TransferEventsOutboxJdbcRepository(jdbc)
    }

    @Test
    @DisplayName("고정 시간으로 Stuck SENDING 상태 테스트")
    fun `test stuck SENDING recovery with fixed time`() {
        // Given - 현재 시간: 2025-01-01 12:00:00
        val baseTime = Instant.parse("2025-01-01T12:00:00Z")

        // 1. 이벤트를 SENDING 상태로 저장 (12:00:00)
        saveEventDirectly(
            eventId = 1L,
            status = "SENDING",
            updatedAt = baseTime
        )

        // When - 12:00:00에 조회 (아직 10분 안 지남)
        val resultsBefore = repository.claimBatchByPartition(
            partition = 0,
            totalPartitions = 1,
            limit = 10,
            now = baseTime
        )

        // Then - 조회되지 않음 (아직 stuck이 아님)
        assertTrue(resultsBefore.isEmpty(), "Should not fetch event within 10 minutes")

        // Given - 시간을 11분 후로 이동 (12:11:00)
        val elevenMinutesLater = baseTime.plusSeconds(660)  // 11분

        // When - 12:11:00에 조회 (10분 초과)
        val resultsAfter = repository.claimBatchByPartition(
            partition = 0,
            totalPartitions = 1,
            limit = 10,
            now = elevenMinutesLater
        )

        // Then - 조회됨 (stuck 상태로 간주되어 복구)
        assertEquals(1, resultsAfter.size, "Should fetch stuck SENDING event")
        assertEquals(1L, resultsAfter[0].eventId)
    }

    @Test
    @DisplayName("next_retry_at 기반 재시도 시간 검증")
    fun `test next retry time with parameter`() {
        // Given - 기준 시간: 2025-01-01 12:00:00
        val baseTime = Instant.parse("2025-01-01T12:00:00Z")

        // 1. 이벤트 저장 (12:00:00)
        val event = TransferEvent(
            eventId = 1L,
            aggregateType = "TRANSFER",
            aggregateId = "tx-1",
            eventType = "COMPLETED",
            payload = """{"amount": 1000}""",
            headers = """{"traceId": "trace-1"}"""
        )
        repository.save(event, now = baseTime)

        // 2. 실패 처리 with 2초 백오프 (12:00:00)
        repository.markFailedWithBackoff(1L, "Test failure", 2000, now = baseTime)

        // When - 1초 후 조회 (12:00:01) - 아직 재시도 시간 안 됨
        val oneSecondLater = baseTime.plusSeconds(1)
        val resultsBefore = repository.claimBatchByPartition(0, 1, 10, now = oneSecondLater)

        // Then - 조회되지 않음
        assertTrue(resultsBefore.isEmpty(), "Should not retry before next_retry_at")

        // When - 3초 후 조회 (12:00:03) - 재시도 시간 지남
        val threeSecondsLater = baseTime.plusSeconds(3)
        val resultsAfter = repository.claimBatchByPartition(0, 1, 10, now = threeSecondsLater)

        // Then - 조회됨
        assertEquals(1, resultsAfter.size, "Should retry after next_retry_at")
    }

    @Test
    @DisplayName("백오프 전략 - 시간 흐름 시나리오")
    fun `test backoff strategy with time simulation`() {
        // Given - 기준 시간
        val baseTime = Instant.parse("2025-01-01T12:00:00Z")

        // 이벤트 저장
        val event = TransferEvent(
            eventId = 1L,
            aggregateType = "TRANSFER",
            aggregateId = "tx-1",
            eventType = "COMPLETED",
            payload = """{"amount": 1000}""",
            headers = """{"traceId": "trace-1"}"""
        )
        repository.save(event, now = baseTime)

        // When - 실패 처리 (5초 백오프)
        repository.markFailedWithBackoff(1L, "Kafka unavailable", 5000, now = baseTime)

        // Then - 4초 후에는 조회 안 됨
        val fourSecondsLater = baseTime.plusSeconds(4)
        val notYet = repository.claimBatchByPartition(0, 1, 10, now = fourSecondsLater)
        assertTrue(notYet.isEmpty(), "Should not retry yet")

        // Then - 6초 후에는 조회됨
        val sixSecondsLater = baseTime.plusSeconds(6)
        val nowRetry = repository.claimBatchByPartition(0, 1, 10, now = sixSecondsLater)
        assertEquals(1, nowRetry.size, "Should retry now")
    }

    @Test
    @DisplayName("여러 이벤트의 재시도 시간 우선순위")
    fun `test multiple events retry priority by time`() {
        // Given - 기준 시간
        val baseTime = Instant.parse("2025-01-01T12:00:00Z")

        // 3개 이벤트 저장
        (1L..3L).forEach { id ->
            repository.save(
                TransferEvent(
                    eventId = id,
                    aggregateType = "TRANSFER",
                    aggregateId = "tx-$id",
                    eventType = "COMPLETED",
                    payload = """{"amount": ${id * 1000}}""",
                    headers = """{"traceId": "trace-$id"}"""
                ), now = baseTime
            )
        }

        // 각각 다른 백오프 시간으로 실패 처리
        repository.markFailedWithBackoff(1L, "Error", 10000, now = baseTime)  // 10초 후
        repository.markFailedWithBackoff(2L, "Error", 5000, now = baseTime)   // 5초 후
        repository.markFailedWithBackoff(3L, "Error", 15000, now = baseTime)  // 15초 후

        // When - 6초 후 조회
        val sixSecondsLater = baseTime.plusSeconds(6)
        val results = repository.claimBatchByPartition(0, 1, 10, now = sixSecondsLater)

        // Then - 이벤트 2번만 조회됨 (5초 백오프)
        assertEquals(1, results.size, "Only event 2 should be ready")
        assertEquals(2L, results[0].eventId, "Event 2 should be first to retry")
    }

    /**
     * 직접 DB에 이벤트를 삽입하는 헬퍼 메서드
     */
    private fun saveEventDirectly(
        eventId: Long,
        status: String = "PENDING",
        updatedAt: Instant
    ) {
        val timestamp = Timestamp.from(updatedAt)  // Instant → Timestamp 변환
        
        val sql = """
            INSERT INTO transfer_events(
                event_id, event_version, aggregate_type, aggregate_id, event_type,
                payload, headers, status, attempt_count, created_at, updated_at, next_retry_at
            ) VALUES (
                :eventId, 1, 'TRANSFER', CONCAT('tx-', CAST(:eventId AS TEXT)), 'COMPLETED',
                '{"amount": 1000}'::jsonb, '{}'::jsonb,
                :status::transfer_outbox_status, 0, :updatedAt, :updatedAt, :updatedAt
            )
        """.trimIndent()

        jdbc.update(sql, mapOf(
            "eventId" to eventId,
            "status" to status,
            "updatedAt" to timestamp  // Timestamp 사용
        ))
    }
}
