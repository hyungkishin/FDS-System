package io.github.hyungkishin.transentia.common.message

data class CoreHeaders(
    val eventType: String,
    val eventVersion: String = "v1",
    val traceId: String,
    val producer: String = "transfer-api",
    val contentType: String = "application/json",
    val extras: Map<String, String> = emptyMap()
)