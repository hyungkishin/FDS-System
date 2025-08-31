package io.github.hyungkishin.transentia.application.required.outbox

data class TransferEvent(
    val eventId: Long,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,   // JSON
    val headers: String    // JSON (traceId 등)
)