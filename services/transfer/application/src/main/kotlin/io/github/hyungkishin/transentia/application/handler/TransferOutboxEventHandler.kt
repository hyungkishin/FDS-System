package io.github.hyungkishin.transentia.application.handler

import io.github.hyungkishin.transentia.application.required.HybridFdsEventPublisher
import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class TransferOutboxEventHandler(
    private val hybridFdsEventPublisher: HybridFdsEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: TransferCompleted) {
        val kafkaSuccess = hybridFdsEventPublisher.publish(event)

        if (!kafkaSuccess) {
            log.warn("Kafka 즉시 전송 실패, Outbox에 저장됨: transactionId={}", event.transactionId)
        }
    }

}