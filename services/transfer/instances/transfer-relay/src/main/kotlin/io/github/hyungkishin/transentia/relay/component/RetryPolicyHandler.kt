package io.github.hyungkishin.transentia.relay.component

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 재시도 정책 전담 클래스
 *
 * 단일 책임: 예외 분석 및 백오프 계산
 */
@Component
class RetryPolicyHandler(
    @Value("\${app.outbox.relay.baseBackoffMs:5000}") private val baseBackoffMs: Long,
    @Value("\${app.outbox.relay.maxBackoffMs:600000}") private val maxBackoffMs: Long
) {

    /**
     * 예외 유형에 따라 재시도 가능 여부 판단
     */
    fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            // 네트워크/일시적 장애 - 재시도 가능
            is org.apache.kafka.common.errors.TimeoutException,
            is org.apache.kafka.common.errors.NetworkException,
            is org.apache.kafka.common.errors.RetriableException -> true

            // 데이터/설정 오류 - 재시도 불필요
            is org.apache.kafka.common.errors.SerializationException,
            is org.apache.kafka.common.errors.InvalidTopicException -> false

            // 기타 예외는 재시도 시도
            else -> true
        }
    }

    /**
     * 지수 백오프 계산 (5초 -> 10초 -> 20초 -> ... 최대 10분)
     */
    fun calculateBackoff(attemptCount: Int): Long {
        return minOf(baseBackoffMs * (1L shl (attemptCount - 1)), maxBackoffMs)
    }
}