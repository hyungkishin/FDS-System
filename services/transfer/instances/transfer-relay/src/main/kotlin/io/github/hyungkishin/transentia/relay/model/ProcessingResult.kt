package io.github.hyungkishin.transentia.relay.model

/**
 * 배치 처리 결과를 담는 데이터 클래스
 */
data class ProcessingResult(
    val successIds: List<Long>,
    val failedEvents: List<FailedEvent>
) {
    val totalProcessed: Int get() = successIds.size + failedEvents.size
    val successRate: Double get() = if (totalProcessed == 0) 0.0 else successIds.size.toDouble() / totalProcessed

    data class FailedEvent(
        val eventId: Long,
        val error: String,
        val attemptCount: Int
    )
}