package io.github.hyungkishin.transentia.publisher

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "publisher")
data class PublisherProperties(
    val poll: Poll = Poll(),
    val topic: Topic = Topic(),
) {
    data class Poll(
        val fixedDelayMs: Long = 500,
        val batchSize: Int = 100,
    )
    data class Topic(
        val default: String = "transfer-events",
        val routes: Map<String, String> = emptyMap()  // aggregateType -> topic
    )
}