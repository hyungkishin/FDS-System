package io.github.hyungkishin.transentia.relay

import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.relay.component.EventBatchProcessor
import io.github.hyungkishin.transentia.relay.component.RetryPolicyHandler
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.ProcessingResult
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService

/**
 * 리팩토링된 Outbox Relay
 *
 * 스케줄링과 전체 플로우 조정
 * 실제 처리는 각 전담 클래스에 위임
 */
@Component
class TransferOutboxRelay(
    private val outboxRepository: TransferEventsOutboxRepository,
    private val eventBatchProcessor: EventBatchProcessor,
    private val retryPolicyHandler: RetryPolicyHandler,
    private val config: OutboxRelayConfig,
    @Qualifier("outboxExecutorService") private val executorService: ExecutorService,
    @Value("\${app.kafka.topics.transfer-events}") private val topicName: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.outbox.relay.fixedDelayMs:1000}")
    fun run() {
        try {
            val startTime = System.currentTimeMillis()

            // 배치 조회
            val batch = outboxRepository.claimBatch(config.batchSize)
            if (batch.isEmpty()) return

            // 배치 처리
            val result = eventBatchProcessor.processBatch(
                batch = batch,
                topicName = topicName,
                timeoutSeconds = config.timeoutSeconds
            )

            val processingTime = System.currentTimeMillis() - startTime

            // 성공 처리
            if (result.successIds.isNotEmpty()) {
                outboxRepository.markAsPublished(result.successIds)
                log.debug("Published {} events ({}% success) in {}ms",
                    result.successIds.size,
                    "%.1f".format(result.successRate * 100),
                    processingTime)
            }

            // 실패 처리
            handleFailedEvents(result.failedEvents)

            // 성능 모니터링
            monitorPerformance(processingTime, result.totalProcessed)

        } catch (e: Exception) {
            log.error("Outbox relay batch processing failed", e)
        }
    }

    /**
     * 실패한 이벤트들에 백오프 적용
     */
    private fun handleFailedEvents(failedEvents: List<ProcessingResult.FailedEvent>) {
        if (failedEvents.isEmpty()) return

        log.warn("Failed to publish {} events", failedEvents.size)

        failedEvents.forEach { failed ->
            val backoff = retryPolicyHandler.calculateBackoff(failed.attemptCount)
            outboxRepository.markFailedWithBackoff(failed.eventId, failed.error, backoff)
        }
    }

    /**
     * 성능 모니터링 및 경고
     */
    private fun monitorPerformance(processingTime: Long, totalProcessed: Int) {
        if (processingTime > config.slowProcessingThresholdMs) {
            log.warn("Slow batch processing: {}ms for {} events (threshold: {}ms)",
                processingTime, totalProcessed, config.slowProcessingThresholdMs)
        }
    }

    /**
     * 애플리케이션 종료 시 정리 작업
     */
    @PreDestroy
    fun cleanup() {
        log.info("Shutting down outbox relay executor service")
        executorService.shutdown()

        try {
            if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown")
                executorService.shutdownNow()

                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate after forced shutdown")
                }
            }
        } catch (e: InterruptedException) {
            log.warn("Interrupted while waiting for executor termination")
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}