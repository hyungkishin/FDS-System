package io.github.hyungkishin.transentia.relay.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaTopicsConfig {

    @Bean
    fun transferEventsTopic(): NewTopic = TopicBuilder
        .name("transfer-events")
        .partitions(8)
        .replicas(1)
        .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
        .build()

    @Bean
    fun transferEventsDlq(): NewTopic = TopicBuilder
        .name("transfer-events-dlq")
        .partitions(4)
        .replicas(1)
        .build()

}