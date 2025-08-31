package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.application.required.outbox.TransferEvent

interface TransferEventsOutboxRepository {
    fun save(row: TransferEvent)
}