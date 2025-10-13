package io.github.hyungkishin.transentia.application.handler

import io.github.hyungkishin.transentia.application.required.HybridFdsEventPublisher
import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TransferOutboxEventHandler(
    private val hybridFdsEventPublisher: HybridFdsEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // TODO : 비동기 방식으로 사용자 경험 UP.
    // ThreadPool 동작 매커니즘
    @Async("outboxEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: TransferCompleted) {

        val currentThread = Thread.currentThread()

        val threadConfigData = mapOf(
            "threadName" to currentThread.name,
            "threadGroup" to (currentThread.threadGroup?.name ?: "N/A"),
            "threadId" to currentThread.id.toString(),
            "isDaemon" to currentThread.isDaemon.toString()
        )

        println("threadConfigData: $threadConfigData")

        val kafkaSuccess = hybridFdsEventPublisher.publish(event)

        if (!kafkaSuccess) {
            log.warn("Kafka 즉시 전송 실패, Outbox에 저장됨: transactionId={}", event.transactionId)
        }
    }

}