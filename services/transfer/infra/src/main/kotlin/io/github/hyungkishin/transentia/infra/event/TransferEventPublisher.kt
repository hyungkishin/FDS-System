package io.github.hyungkishin.transentia.infra.event

import io.github.hyungkishin.transentia.application.required.event.EventPublisherPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * NOTICE: 송금 이후, db commit 이후 fds 에 event Publishing
 * @see KafkaAfterCommitForwarder
 */
@Component
class TransferEventPublisher(
    private val delegate: ApplicationEventPublisher
) : EventPublisherPort {

    override fun publish(event: Any) {
        // 트랜잭션 안에서 발행한다. ( 리스너가 AFTER_COMMIT 으로 보증하기 위함 )
        delegate.publishEvent(event)
    }

}