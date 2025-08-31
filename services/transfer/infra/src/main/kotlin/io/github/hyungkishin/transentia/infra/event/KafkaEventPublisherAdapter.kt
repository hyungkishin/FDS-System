package io.github.hyungkishin.transentia.infra.event

import io.github.hyungkishin.transentia.common.message.HeaderCodec
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class KafkaEventPublisherAdapter(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    private val headerCodec: HeaderCodec,
    @Value("\${app.transfer.topic}") private val topic: String
) {
    fun publish(key: String, payloadJson: String, outboxHeadersJson: String? = null) {
        val record = ProducerRecord(
            topic, null, key, payloadJson.toByteArray(StandardCharsets.UTF_8)
        )

        // Outbox.headers(JSON) → Map → Kafka Headers
        val flat = headerCodec.jsonToFlatMap(outboxHeadersJson)
        val enriched = flat + mapOf(
            "traceId" to (flat["traceId"] ?: UUID.randomUUID().toString()),
            "contentType" to (flat["contentType"] ?: "application/json")
        )
        KafkaHeadersSupport.putAll(enriched, record.headers())

        kafkaTemplate.send(record).get()
    }
}