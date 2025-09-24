package io.github.hyungkishin.transentia.relay.config

import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeader
import java.nio.charset.StandardCharsets

object KafkaHeadersSupport {
    fun putAll(map: Map<String, String>, headers: Headers) {
        map.forEach { (k, v) ->
            while (headers.lastHeader(k) != null) headers.remove(k)
            headers.add(RecordHeader(k, v.toByteArray(StandardCharsets.UTF_8)))
        }
    }

    fun toMap(headers: Headers): Map<String, String> =
        headers.associate { it.key() to (it.value()?.toString(StandardCharsets.UTF_8) ?: "") }
}