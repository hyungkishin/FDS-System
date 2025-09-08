package io.github.hyungkishin.transentia.common.trace

import org.slf4j.MDC
import java.util.*

object TraceId {
    const val HEADER_NAME = "X-Trace-Id"

    fun generate32Hex(): String = UUID.randomUUID().toString().replace("-", "").lowercase()

    fun put(traceId: String) = MDC.put(HEADER_NAME, traceId)

    fun getOrNew(): String = MDC.get(HEADER_NAME) ?: generate32Hex()

    fun remove() = MDC.remove(HEADER_NAME)
}
