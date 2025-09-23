package io.github.hyungkishin.transentia.relay.component

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventType
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferStatus
import io.github.hyungkishin.transentia.relay.model.ProcessingResult
import io.github.hyungkishin.transentia.relay.model.TransferPayload
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * 이벤트 배치 처리 전담 클래스
 *
 * 이벤트 목록을 받아서 병렬 처리하고 결과를 반환한다.
 */
@Component
class EventBatchProcessor(
    private val kafkaEventPublisher: KafkaEventPublisher,
    private val objectMapper: ObjectMapper,
    private val retryPolicyHandler: RetryPolicyHandler,
    private val executorService: ExecutorService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 배치를 청크 단위로 나누어 병렬 처리
     */
    fun processBatch(
        batch: List<ClaimedRow>,
        topicName: String,
        chunkSize: Int = Runtime.getRuntime().availableProcessors() * 2,
        timeoutSeconds: Long = 5
    ): ProcessingResult {
        val successIds = Collections.synchronizedList(mutableListOf<Long>())
        val failedEvents = Collections.synchronizedList(mutableListOf<ProcessingResult.FailedEvent>())

        batch.chunked(chunkSize).forEach { chunk ->
            processChunk(chunk, topicName, timeoutSeconds, successIds, failedEvents)
        }

        return ProcessingResult(
            successIds = successIds.toList(),
            failedEvents = failedEvents.toList()
        )
    }

    private fun processChunk(
        chunk: List<ClaimedRow>,
        topicName: String,
        timeoutSeconds: Long,
        successIds: MutableList<Long>,
        failedEvents: MutableList<ProcessingResult.FailedEvent>
    ) {
        val futures = chunk.map { row ->
            CompletableFuture.supplyAsync({
                processEvent(row, topicName)
            }, executorService)
        }

        // 청크별 완료 대기
        futures.forEach { future ->
            try {
                val result = future.get(timeoutSeconds, TimeUnit.SECONDS)
                if (result.isSuccess) {
                    successIds.add(result.eventId)
                } else {
                    failedEvents.add(result.toFailedEvent())
                }
            } catch (e: Exception) {
                log.warn("Future processing failed: ${e.message}")
            }
        }
    }

    private fun processEvent(row: ClaimedRow, topicName: String): EventProcessingResult {
        return try {
            val eventModel = createKafkaEventModel(row)
            kafkaEventPublisher.publish(topicName, row.aggregateId, eventModel)
            return EventProcessingResult.success(row.eventId)
        } catch (e: Exception) {
            val shouldRetry = retryPolicyHandler.shouldRetry(e)
            val errorMessage = if (shouldRetry) {
                e.message ?: "Send failed"
            } else {
                "Non-retryable error: ${e.message ?: "Send failed"}"
            }
            return EventProcessingResult.failure(row.eventId, errorMessage, if (shouldRetry) 0 else 999)
        }
    }

    private fun createKafkaEventModel(row: ClaimedRow): TransferEventAvroModel {
        val payload = objectMapper.readValue(row.payload, TransferPayload::class.java)

        return TransferEventAvroModel.newBuilder()
            .setEventId(row.eventId)
            .setEventType(determineEventType(payload))
            .setAggregateId(row.aggregateId)
            .setTransactionId(payload.transactionId)
            .setSenderId(payload.senderId)
            .setReceiverId(payload.receiverUserId)
            .setAmount(payload.amount.toString())
            .setStatus(TransferStatus.valueOf(payload.status))
            .setOccurredAt(payload.occurredAt)
            .setHeaders(row.headers)
            .setCreatedAt(System.currentTimeMillis())
            .build()
    }

    private fun determineEventType(payload: TransferPayload): TransferEventType {
        return when (payload.status) {
            "COMPLETED" -> TransferEventType.TRANSFER_COMPLETED
            "FAILED" -> TransferEventType.TRANSFER_FAILED
            else -> TransferEventType.TRANSFER_COMPLETED
        }
    }
}

/**
 * 개별 이벤트 처리 결과
 */
private data class EventProcessingResult(
    val eventId: Long,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val attemptCount: Int = 0
) {
    companion object {
        fun success(eventId: Long) = EventProcessingResult(eventId, true)
        fun failure(eventId: Long, error: String, attemptCount: Int) =
            EventProcessingResult(eventId, false, error, attemptCount)
    }

    fun toFailedEvent() = ProcessingResult.FailedEvent(
        eventId = eventId,
        error = errorMessage ?: "Unknown error",
        attemptCount = attemptCount
    )
}