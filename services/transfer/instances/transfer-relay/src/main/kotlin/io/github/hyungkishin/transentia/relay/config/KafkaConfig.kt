package io.github.hyungkishin.transentia.relay.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrap: String
) {

    @Bean
    fun producerFactory(): ProducerFactory<String, ByteArray> {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            // 소형 배치 최적화
            ProducerConfig.BATCH_SIZE_CONFIG to 32 * 1024,  // 32KB, 배치 단위 크기(32KB). 16KB 기본보다 크게 잡아, 대량 메시지 전송 효율화.
            ProducerConfig.LINGER_MS_CONFIG to 10, // 프로듀서가 배치를 모아서 보내기 전에 기다리는 시간 10ms
            ProducerConfig.COMPRESSION_TYPE_CONFIG to "lz4", // 메시지를 LZ4 압축해서 전송. ( 압축/해제 CPU 부하는 있지만 보통 trade-off 가치 있음. )
            ProducerConfig.ACKS_CONFIG to "1", // 모든 ISR(replica)에 기록될 때 ack. ( 단, 지연(latency)은 약간 증가. )
            ProducerConfig.RETRIES_CONFIG to 3, // 네트워크/브로커 일시 오류 시 재시도 횟수.
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5 // 연결당 동시 전송 요청 개수.
        )
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, ByteArray> = KafkaTemplate(producerFactory())

}