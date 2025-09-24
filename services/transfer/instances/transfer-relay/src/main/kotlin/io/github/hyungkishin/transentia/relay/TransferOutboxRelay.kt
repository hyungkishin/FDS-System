package io.github.hyungkishin.transentia.relay

import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import io.github.hyungkishin.transentia.relay.config.KafkaEventPublisherAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// TODO: 해당 Layer 를 간단하게.
@Component
class TransferOutboxRelay(
    private val outbox: TransferEventsOutboxJdbcRepository,
    private val publisher: KafkaEventPublisherAdapter,
    @Value("\${app.outbox.relay.batchSize:300}") private val batchSize: Int,
    @Value("\${app.outbox.relay.baseBackoffMs:1000}") private val baseBackoffMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.outbox.relay.fixedDelayMs:1000}")
    fun run() {
        val batch = outbox.claimBatch(batchSize)
        if (batch.isEmpty()) return

        val successIds = mutableListOf<Long>()
        var failed = 0

        batch.forEach { row ->
            try {
                publisher.publish(
                    key = row.aggregateId, // 어떤 값을 넣으면 좋을지 ?
                    payloadJson = row.payload,
                    outboxHeadersJson = row.headers
                )
                successIds += row.eventId
            } catch (e: Exception) {
                outbox.markFailedWithBackoff(row.eventId, e.message, baseBackoffMs)
                failed++
                log.error("outbox publish failed id={}, cause={}", row.eventId, e.message)
            }
        }

        if (successIds.isNotEmpty()) outbox.deleteSucceeded(successIds)
        if (successIds.isNotEmpty() || failed > 0) {
            log.info("relay batch done: success={}, failed={}", successIds.size, failed)
        }
    }
}