package io.github.hyungkishin.transentia.infra.event

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KafkaTopicsConfig {
    @Bean fun transferEvents(): NewTopic = NewTopic("transfer.events.v1", 12, 1.toShort())
        .configs(mapOf("retention.ms" to "1209600000"))

    @Bean fun transferEventsRetry() = NewTopic("transfer.events.retry.v1", 12, 1.toShort())

    @Bean fun transferEventsDlq() = NewTopic("transfer.events.dlq.v1", 12, 1.toShort())

}