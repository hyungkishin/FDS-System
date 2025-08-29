package io.github.hyungkishin.transentia.infra.event

import io.github.hyungkishin.transentia.infra.event.mapper.toContract
import io.github.hyungkishin.transentia.consumer.event.TransferCompletedEvent
import io.github.hyungkishin.transentia.consumer.event.TransferFailedEvent
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * DB 커밋 성공 후에만 해당 리스너가 실행된다.
 * 이벤트가 publish 되었으나, DB 가 롤백이 될경우.
 */
@Component
class KafkaAfterCommitForwarder(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(e: TransferCompletedEvent) {
        val c = e.toContract()
        kafkaTemplate.send("transfer.completed", c.txId.toString(), c)

        // Kafka Infra (= 사용측면에서 내부 운영 방침과 동작방식 사례 - 지인 찬스 )
        // Topic partition, key ( header 가 어떤 목적으로 key value 가 설정되는지, )
        // 파티션(설계) 에 따라 Consumer 가 몇대가 될지
        // Header 에 어떤 값이 있어야 하는지
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(e: TransferFailedEvent) {
        val c = e.toContract()
        kafkaTemplate.send("transfer.failed", c.txId.toString(), c)
    }

}