package io.github.hyungkishin.transentia.application.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.domain.event.TransferEvent
import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import io.github.hyungkishin.transentia.common.message.transfer.TransferFailed
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID

@Component
class TransferOutboxEventHandler(
    private val outboxRepository: TransferEventsOutboxRepository,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: TransferCompleted) {
        val outboxEvent = createOutboxEvent(
            eventType = "TRANSFER_COMPLETED",
            aggregateId = event.transactionId,
            payload = mapOf(
                "transactionId" to event.transactionId,
                "senderId" to event.senderUserId,
                "receiverId" to event.receiverUserId,
                "amount" to event.amount,
                "status" to "COMPLETED",
                "occurredAt" to event.occurredAt.toEpochMilli()
            ),
            eventVersion = "TransferCompleted"
        )

        outboxRepository.save(outboxEvent)
        log.info("Transfer completed event saved to outbox: transactionId={}", event.transactionId)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: TransferFailed) {
        val outboxEvent = createOutboxEvent(
            eventType = "TRANSFER_FAILED",
            aggregateId = event.transactionId,
            payload = mapOf(
                "transactionId" to event.transactionId,
                "status" to "FAILED",
                "reason" to event.reason,
                "occurredAt" to event.occurredAt.toEpochMilli()
            ),
            eventVersion = "TransferFailed"
        )

        outboxRepository.save(outboxEvent)
        log.info("Transfer failed event saved to outbox: transactionId={}", event.transactionId)
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