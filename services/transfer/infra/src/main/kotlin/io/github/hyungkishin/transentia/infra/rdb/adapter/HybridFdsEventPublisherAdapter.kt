package io.github.hyungkishin.transentia.infra.rdb.adapter

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.application.required.HybridFdsEventPublisher
import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.domain.event.TransferEvent
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventType
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferStatus
import io.github.hyungkishin.transentia.infrastructure.kafka.producer.service.KafkaProducer
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class HybridFdsEventPublisherAdapter(
    private val kafkaProducer: KafkaProducer<String, TransferEventAvroModel>,
    private val outboxRepository: TransferEventsOutboxRepository,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper,
    @Value("\${app.kafka.topics.transfer-events}") private val topicName: String
) : HybridFdsEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: TransferCompleted): Boolean {

        return try {
            val transferModel = TransferEventAvroModel.newBuilder()
                .setEventId(idGenerator.nextId())
                .setEventType(TransferEventType.TRANSFER_COMPLETED)
                .setAggregateId(event.transactionId.toString())
                .setTransactionId(event.transactionId)
                .setSenderId(event.senderUserId)
                .setReceiverId(event.receiverUserId)
                .setAmount(event.amount.toString())
                .setStatus(TransferStatus.COMPLETED)
                .setOccurredAt(event.occurredAt.toEpochMilli())
                .setHeaders(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "eventType" to "TRANSFER_COMPLETED",
                            "eventVersion" to "v1",
                            "traceId" to currentTraceId(),
                            "producer" to "transfer-api",
                            "contentType" to "application/json"
                        )
                    )
                )
                .setCreatedAt(System.currentTimeMillis())
                .build()

            kafkaProducer.sendSync(topicName, event.transactionId.toString(), transferModel)
            true
        } catch (e: Exception) {
            // 실패시, outbox 테이블에 적재. ( 실패한 이벤트는 relay 서버에서 실행한다. )
            saveToOutTransferOutbox(event)
            false
        }

    }

    private fun saveToOutTransferOutbox(event: TransferCompleted) {

        try {
            val outboxEvent = createOutboxEvent(
                eventType = "TRANSFER_COMPLETED",
                aggregateId = event.transactionId,
                payload = mapOf(
                    "transactionId" to event.transactionId,
                    "senderId" to event.senderUserId,
                    "receiverUserId" to event.receiverUserId,
                    "amount" to event.amount,
                    "status" to "COMPLETED",
                    "occurredAt" to event.occurredAt.toEpochMilli()
                ),
                eventVersion = "TransferCompleted"
            )

            outboxRepository.save(outboxEvent)

            log.info("Transfer completed event saved to outbox: transactionId={}", event.transactionId)
        } catch (e: Exception) {
            log.error("Failed to save transfer completed event to outbox: transactionId={}", event.transactionId, e)
        }
    }

    private fun createOutboxEvent(
        eventType: String,
        aggregateId: Long,
        payload: Map<String, Any>,
        eventVersion: String
    ): TransferEvent {
        return TransferEvent(
            eventId = idGenerator.nextId(),
            aggregateType = "Transaction",
            aggregateId = aggregateId.toString(),
            eventType = eventType,
            payload = objectMapper.writeValueAsString(payload),
            headers = objectMapper.writeValueAsString(
                mapOf(
                    "eventType" to eventVersion,
                    "eventVersion" to "v1",
                    "traceId" to currentTraceId(),
                    "producer" to "transfer-api",
                    "contentType" to "application/json"
                )
            )
        )
    }

    private fun currentTraceId(): String =
        MDC.get("traceId") ?: UUID.randomUUID().toString()

}