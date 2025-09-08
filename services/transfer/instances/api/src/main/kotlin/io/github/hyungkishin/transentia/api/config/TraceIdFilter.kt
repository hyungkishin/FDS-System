package io.github.hyungkishin.transentia.api.config

import io.github.hyungkishin.transentia.common.trace.TraceId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        private val HEX_32 = Regex("^[0-9a-f]{32}$")
        private const val KEY = "X-Trace-Id"
    }

    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val incoming = req.getHeader(KEY)
        val traceId = normalizeOrNew(incoming) // 항상 32-hex

        try {
            // MDC / 헤더 / RequestAttr 모두 같은 키로 통일
            MDC.put(KEY, traceId)
            TraceId.put(traceId)
            req.setAttribute(KEY, traceId)
            res.setHeader(KEY, traceId)

            chain.doFilter(req, res)
        } finally {
            MDC.remove(KEY) // 삭제 해야하는 이유.
            TraceId.remove()
        }
    }

    private fun normalizeOrNew(raw: String?): String {
        val normalized = raw?.trim()?.lowercase()
        if (normalized != null && normalized.matches(HEX_32) && !normalized.all { it == '0' }) {
            return normalized
        }
        // 32-hex 생성으로 통일
        return TraceId.generate32Hex()
    }
}
