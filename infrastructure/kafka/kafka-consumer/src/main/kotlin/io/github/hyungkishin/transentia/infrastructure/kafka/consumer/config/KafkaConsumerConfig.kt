package io.github.hyungkishin.transentia.infrastructure.kafka.consumer.config

import io.github.hyungkishin.transentia.infrastructure.kafka.config.KafkaConfigData
import io.github.hyungkishin.transentia.infrastructure.kafka.config.KafkaConsumerConfigData
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import java.io.Serializable

@Configuration
class KafkaConsumerConfig<K : Serializable, V : SpecificRecordBase>(
    private val kafkaConfigData: KafkaConfigData,
    private val kafkaConsumerConfigData: KafkaConsumerConfigData
) {

    @Bean
    fun consumerConfigs(): Map<String, Any> {
        return mutableMapOf<String, Any>().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.bootstrapServers)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, kafkaConsumerConfigData.keyDeserializer)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, kafkaConsumerConfigData.valueDeserializer)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaConsumerConfigData.autoOffsetReset)
            put(kafkaConfigData.schemaRegistryUrlKey, kafkaConfigData.schemaRegistryUrl)
            put(kafkaConsumerConfigData.specificAvroReaderKey, kafkaConsumerConfigData.specificAvroReader)
            put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaConsumerConfigData.sessionTimeoutMs)
            put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, kafkaConsumerConfigData.heartbeatIntervalMs)
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, kafkaConsumerConfigData.maxPollIntervalMs)
            put(
                ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
                kafkaConsumerConfigData.maxPartitionFetchBytesDefault * kafkaConsumerConfigData.maxPartitionFetchBytesBoostFactor
            )
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaConsumerConfigData.maxPollRecords)
        }
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<K, V> {
        return DefaultKafkaConsumerFactory(consumerConfigs())
    }

    @Bean
    fun kafkaListenerContainerFactory(): KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<K, V>> {
        val factory = ConcurrentKafkaListenerContainerFactory<K, V>()
        factory.consumerFactory = consumerFactory()
        factory.isBatchListener = kafkaConsumerConfigData.batchListener
        factory.setConcurrency(kafkaConsumerConfigData.concurrencyLevel)
        factory.setAutoStartup(kafkaConsumerConfigData.autoStartup)
        factory.containerProperties.pollTimeout = kafkaConsumerConfigData.pollTimeoutMs
        return factory
    }
}