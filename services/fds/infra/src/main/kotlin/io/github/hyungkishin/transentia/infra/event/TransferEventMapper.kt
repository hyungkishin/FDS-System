package io.github.hyungkishin.transentia.infra.event

import io.github.hyungkishin.transentia.container.event.TransferCompleteEvent
import io.github.hyungkishin.transentia.infrastructure.kafka.model.TransferEventAvroModel
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class TransferEventMapper {

    fun toDomain(avroModel: TransferEventAvroModel): TransferCompleteEvent {
        return TransferCompleteEvent(
            eventId = avroModel.eventId,
            senderId = avroModel.senderId,
            receiverId = avroModel.receiverId,
            amount = avroModel.amount.toLong(), // Avro amount는 rawValue를 String으로 저장
            status = avroModel.status.toString(),
            occurredAt = Instant.ofEpochMilli(avroModel.occurredAt)
        )
    }
}
