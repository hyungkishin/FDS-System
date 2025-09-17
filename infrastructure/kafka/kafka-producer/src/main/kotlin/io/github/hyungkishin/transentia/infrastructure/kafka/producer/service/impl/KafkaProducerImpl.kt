package io.github.hyungkishin.transentia.infrastructure.kafka.producer.service.impl

import io.github.hyungkishin.transentia.infrastructure.kafka.producer.exception.KafkaProducerException
import io.github.hyungkishin.transentia.infrastructure.kafka.producer.service.KafkaProducer
import jakarta.annotation.PreDestroy
import org.apache.avro.specific.SpecificRecordBase
import org.slf4j.LoggerFactory
import org.springframework.kafka.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import java.io.Serializable
import java.util.function.BiConsumer

@Component
class KafkaProducerImpl<K : Serializable, V : SpecificRecordBase>(
    private val kafkaTemplate: KafkaTemplate<K, V>
) : KafkaProducer<K, V> {

    companion object {
        private val log = LoggerFactory.getLogger(KafkaProducerImpl::class.java)
    }

    override fun send(topicName: String, key: K, message: V, callback: BiConsumer<SendResult<K, V>, Throwable>) {
        log.info("Sending message={} to topic={}", message, topicName)
        try {
            val kafkaResultFuture = kafkaTemplate.send(topicName, key, message)
            kafkaResultFuture.whenComplete(callback)
        } catch (e: KafkaException) {
            log.error("Error on kafka producer with key: {}, message: {} and exception: {}", key, message, e.message)
            throw KafkaProducerException("Error on kafka producer with key: $key and message: $message")
        }
    }

    @PreDestroy
    fun close() {
        log.info("Closing kafka producer!")
        kafkaTemplate.destroy()
    }
}