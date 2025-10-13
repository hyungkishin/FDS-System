package io.github.hyungkishin.transentia.relay.component

import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import io.github.hyungkishin.transentia.infrastructure.kafka.producer.service.KafkaProducer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Kafka 이벤트 발행 전담 클래스
 *
 * 단일 책임: Kafka로 메시지를 안전하게 전송
 */
@Component
class KafkaEventPublisher(
    private val kafkaProducer: KafkaProducer<String, TransferEventAvroModel>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트를 Kafka로 동기 전송
     *
     * @throws Exception 전송 실패 시 예외 발생
     */
    fun publish(topicName: String, event: TransferEventAvroModel) {
        try {
            kafkaProducer.sendSync(topicName, event)
            log.debug("Successfully published event: eventId={}, type={}",
                event.eventId, event.eventType)
        } catch (e: Exception) {
            log.error("Failed to publish event: eventId={}, error={}",
                event.eventId, e.message, e)
            // 재전송을 위해 예외를 다시 던짐
            throw e
        }
    }
}