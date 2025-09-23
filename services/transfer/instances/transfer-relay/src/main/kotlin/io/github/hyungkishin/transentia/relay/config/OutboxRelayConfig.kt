package io.github.hyungkishin.transentia.relay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Outbox 설정 프로퍼티
 */
@ConfigurationProperties(prefix = "app.outbox.relay")
data class OutboxRelayConfig(
    val batchSize: Int = 500,
    val fixedDelayMs: Long = 1000,
    val threadPoolSize: Int = 8,
    val timeoutSeconds: Long = 5,
    val baseBackoffMs: Long = 5000,
    val maxBackoffMs: Long = 600000,
    val slowProcessingThresholdMs: Long = 3000
)