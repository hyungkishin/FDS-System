package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.domain.event.TransferEvent

interface TransferEventsOutboxRepository {
    fun save(row: TransferEvent)
}