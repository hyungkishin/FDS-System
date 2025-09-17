package io.github.hyungkishin.transentia.infra.rdb.adapter

import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.domain.event.TransferEvent
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

data class ClaimedRow(
    val eventId: Long,
    val aggregateId: String,
    val payload: String,
    val headers: String
)

@Repository
class TransferEventsOutboxJdbcRepository(
    val jdbc: NamedParameterJdbcTemplate
) : TransferEventsOutboxRepository {

    override fun save(row: TransferEvent) {
        val sql = """
            INSERT INTO transfer_events(
              event_id, event_version, aggregate_type, aggregate_id, event_type,
              payload, headers, status, attempt_count, created_at, updated_at
            ) VALUES (:eventId, 1, :aggType, :aggId, :eventType,
                     CAST(:payload AS JSONB), CAST(:headers AS JSONB),
                     'PENDING', 0, now(), now())
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
     * Lock
     */
    fun claimBatch(limit: Int): List<ClaimedRow> {
        val sql = """
          WITH grabbed AS (
            SELECT event_id
            FROM transfer_events
            WHERE status = 'PENDING'
              AND next_retry_at <= now()
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
          )
          UPDATE transfer_events t
             SET status = 'SENDING',
                 attempt_count = attempt_count + 1,
                 updated_at = now()
            FROM grabbed g
           WHERE t.event_id = g.event_id
          RETURNING t.event_id, t.aggregate_id, t.payload::text AS payload, t.headers::text AS headers
        """.trimIndent()

         // payload 에 담겨있는 JSON = {"amount": 500000000000, "status": "COMPLETED", "senderId": 10001, "occurredAt": 1758142742082, "receiverId": 10002, "transactionId": 226809262431539200}
        val mapper = RowMapper { rs, _ ->
            ClaimedRow(
                eventId = rs.getLong("event_id"),
                aggregateId = rs.getString("aggregate_id"),
                payload = rs.getString("payload"),
                headers = rs.getString("headers")
            )
        }
        return jdbc.query(sql, mapOf("limit" to limit), mapper)
    }

    fun deleteSucceeded(ids: List<Long>) {
        if (ids.isEmpty()) return
        jdbc.update("DELETE FROM transfer_events WHERE event_id IN (:ids)", mapOf("ids" to ids))
    }

    fun markFailedWithBackoff(id: Long, cause: String?, backoffMillis: Long) {
        val sql = """
          UPDATE transfer_events
             SET status='FAILED',
                 last_error=:cause,
                 updated_at = now(),
                 next_retry_at = now() + (:backoff || ' milliseconds')::interval
           WHERE event_id=:id
        """.trimIndent()
        jdbc.update(sql, mapOf("id" to id, "cause" to (cause ?: "UNKNOWN"), "backoff" to backoffMillis))
    }
}
