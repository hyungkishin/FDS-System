package io.github.hyungkishin.transentia.infra.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component

@Component
class TransferEventsConsumer(
    private val objectMapper: ObjectMapper
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
        payload: ByteArray,
        @Header(KafkaHeaders.RECEIVED_KEY) key: String?,
        @org.springframework.messaging.handler.annotation.Headers headers: Map<String, Any>
    ) {
        val json = String(payload)
        val node: JsonNode = objectMapper.readTree(json)

        fun headerString(name: String): String? =
            (headers[name] as? ByteArray)?.toString(Charsets.UTF_8)

        val eventType = headerString("eventType")
        val traceId = headerString("X-Trace-Id")
        val eventId = node.get("eventId")?.asLong()

        log.info(
            "[FDS] RECEIVED key={} eventType={} eventId={} traceId={} json={}",
            key, eventType, eventId, traceId, json
        )
    }
}