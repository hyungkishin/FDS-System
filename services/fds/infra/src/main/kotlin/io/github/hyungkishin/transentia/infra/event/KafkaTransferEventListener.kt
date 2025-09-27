package io.github.hyungkishin.transentia.infra.event

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Headers
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class TransferEventsConsumer(
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 대량에 메세지 상황
    // offset
    // partition 몇대 ? stream 이 이벤트를 소비할때, 어느파티션에서 소비 하는지, 그림으로
    // 순서 보장 ?
    // - consumer pod 가 여러대.
    // - 동시에 여러 이벤트를 소비해야하는데, 실패되어야 하는 상황에서 통과 되는 시나리오

    @KafkaListener(
        topics = ["\${app.transfer.topic}"],
        groupId = "\${spring.kafka.consumer.group-id}"
    )
    fun onMessage(
        @Payload transferEvent: TransferEventAvroModel,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String?,
        @Headers headers: Map<String, Any>
    ) {
        val eventType = headers?.get("eventType")?.toString()
        val traceId = headers?.get("X-Trace-Id")?.toString()

        print("transferEvent: $transferEvent")

        log.info(
            "[FDS] RECEIVED key={} eventType={} eventId={} amount={} status={} traceId={}",
            key, eventType, transferEvent.eventId, transferEvent.amount,
            transferEvent.status, traceId
        )
    }

}