package io.github.hyungkishin.transentia.infra.event


import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@ConditionalOnProperty(prefix = "app.relay", name = ["enabled"], havingValue = "true")
@Component
class TransferOutboxRelay(
    private val outbox: TransferEventsOutboxJdbcRepository,
    private val publisher: KafkaEventPublisherAdapter,
    @Value("\${app.outbox.relay.batchSize:200}") private val batchSize: Int,
    @Value("\${app.outbox.relay.baseBackoffMs:200}") private val baseBackoffMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 작은 주기 배치 (실시간에 가까움)
    @Scheduled(fixedDelayString = "\${app.outbox.relay.fixedDelayMs:200}", scheduler = "relayScheduler")
    fun run() {
        val batch = outbox.claimBatch(batchSize)
        if (batch.isEmpty()) return

        val successIds = mutableListOf<Long>()
        var failed = 0

        batch.forEach { row ->
            try {
                publisher.publish(
                    key = row.aggregateId,
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
