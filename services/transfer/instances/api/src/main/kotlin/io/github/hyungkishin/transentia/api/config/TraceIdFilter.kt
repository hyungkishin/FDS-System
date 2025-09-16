package io.github.hyungkishin.transentia.api.config

import io.github.hyungkishin.transentia.common.trace.TraceId
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val traceId = TraceId.generate()
        try {
            TraceId.put(traceId)
            req.setAttribute(TraceId.HEADER_NAME, traceId)
            res.setHeader(TraceId.HEADER_NAME, traceId)
            chain.doFilter(req, res)
        } finally {
            TraceId.clear()
        }
    }
}
