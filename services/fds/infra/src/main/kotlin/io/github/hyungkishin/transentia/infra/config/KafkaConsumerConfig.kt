package io.github.hyungkishin.transentia.infra.config

import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import io.github.hyungkishin.transentia.common.message.transfer.TransferFailed
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.support.serializer.JsonDeserializer

@Configuration
class KafkaConsumerConfig(
    private val consumerFactory: ConsumerFactory<String, Any>
) {
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.consumerFactory = consumerFactory
        return factory
    }

    @Bean
    fun transferCompletedDeserializer(): JsonDeserializer<TransferCompleted> {
        val deserializer = JsonDeserializer(TransferCompleted::class.java)
        deserializer.addTrustedPackages("io.github.hyungkishin.transentia.common.message")
        return deserializer
    }

    @Bean
    fun transferFailedDeserializer(): JsonDeserializer<TransferFailed> {
        val deserializer = JsonDeserializer(TransferFailed::class.java)
        deserializer.addTrustedPackages("io.github.hyungkishin.transentia.common.message")
        return deserializer
    }
}
