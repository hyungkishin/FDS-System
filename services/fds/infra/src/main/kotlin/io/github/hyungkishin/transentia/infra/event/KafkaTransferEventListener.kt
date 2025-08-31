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

        val eventType = headerString("eventType") ?: "Unknown"
        val traceId = headerString("traceId")
        val eventId = node.get("eventId")?.asLong()

        log.info(
            "[FDS] RECEIVED key={} eventType={} eventId={} traceId={} json={}",
            key, eventType, eventId, traceId, json
        )
    }
}