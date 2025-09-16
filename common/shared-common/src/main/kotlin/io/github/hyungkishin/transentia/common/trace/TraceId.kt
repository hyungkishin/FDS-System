package io.github.hyungkishin.transentia.common.trace

import org.slf4j.MDC
import java.util.*

object TraceId {
    const val HEADER_NAME = "X-Trace-Id"

    fun generate(): String = UUID.randomUUID().toString()

    fun getOrNew(): String = MDC.get(HEADER_NAME) ?: generate()

    fun put(traceId: String) = MDC.put(HEADER_NAME, traceId)

    fun clear() = MDC.clear()
}