package io.github.hyungkishin.transentia.relay

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventType
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferStatus
import io.github.hyungkishin.transentia.infrastructure.kafka.producer.service.KafkaProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TransferOutboxRelay(
    private val outboxRepository: TransferEventsOutboxJdbcRepository,
    private val kafkaProducer: KafkaProducer<String, TransferEventAvroModel>,
    private val objectMapper: ObjectMapper,
    @Value("\${app.outbox.relay.batchSize:300}") private val batchSize: Int,
    @Value("\${app.outbox.relay.baseBackoffMs:1000}") private val baseBackoffMs: Long,
    @Value("\${app.kafka.topics.transfer-events}") private val topicName: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.outbox.relay.fixedDelayMs:1000}")
    fun run() {
        val batch = outboxRepository.claimBatch(batchSize)
        if (batch.isEmpty()) return

        val successIds = mutableListOf<Long>()
        var failed = 0

        batch.forEach { row ->
            try {
                val payloadData = objectMapper.readValue(row.payload, Map::class.java)

                val avroModel = TransferEventAvroModel.newBuilder()
                    .setEventId(row.eventId)
                    .setEventType(TransferEventType.TRANSFER_COMPLETED)
                    .setAggregateId(row.aggregateId)
                    .setTransactionId(payloadData["transactionId"] as? Long ?: 0L)
                    .setSenderId(payloadData["senderId"] as? Long ?: 0L)
                    .setReceiverId(payloadData["receiverId"] as? Long ?: 0L)
                    .setAmount(payloadData["amount"]?.toString() ?: "0")
                    .setStatus(TransferStatus.valueOf(payloadData["status"] as? String ?: "COMPLETED"))
                    .setOccurredAt(payloadData["occurredAt"] as? Long ?: System.currentTimeMillis())
                    .setHeaders(row.headers ?: "{}")
                    .setCreatedAt(System.currentTimeMillis())
                    .build()

                // Infrastructure KafkaProducer 사용
                kafkaProducer.send(
                    topicName = topicName,
                    key = row.aggregateId,
                    message = avroModel,
                    callback = { _: SendResult<String, TransferEventAvroModel>?, ex: Throwable? ->
                        if (ex != null) {
                            log.error("Kafka callback error for eventId={}: {}", row.eventId, ex.message)
                        } else {
                            log.debug("Event published successfully: eventId={}", row.eventId)
                        }
                    }
                )
                successIds += row.eventId
            } catch (e: Exception) {
                outboxRepository.markFailedWithBackoff(row.eventId, e.message, baseBackoffMs)
                failed++
                log.error("outbox publish failed id={}, cause={}", row.eventId, e.message)
            }
        }

        if (successIds.isNotEmpty()) outboxRepository.deleteSucceeded(successIds)
        if (successIds.isNotEmpty() || failed > 0) {
            log.info("relay batch done: success={}, failed={}", successIds.size, failed)
        }
    }
}