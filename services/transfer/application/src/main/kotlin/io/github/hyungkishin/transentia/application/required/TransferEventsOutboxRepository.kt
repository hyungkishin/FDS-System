package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.container.event.TransferEvent
import java.time.Instant

interface TransferEventsOutboxRepository {

    fun save(row: TransferEvent, now: Instant)

    /**
     * 처리할 이벤트들을 배치로 조회하고 SENDING 상태로 변경한다.
     *
     * 여러 스레드나 프로세스가 동시에 실행되어도 안전하도록 SKIP LOCKED를 사용한다.
     * Stuck SENDING 상태(stuckThresholdSeconds 이상 진행 중)인 이벤트도 자동으로 복구하여 처리한다.
     * 우선순위는 PENDING > SENDING(Stuck) > FAILED 순으로 처리한다.
     *
     * @param limit 한 번에 처리할 최대 이벤트 수
     * @param now 기준 시간 (기본값: 현재 시간, 테스트 시 고정 시간 주입 가능)
     * @param stuckThresholdSeconds Stuck SENDING 판단 기준 시간 (초)
     * @return 처리할 이벤트 목록
     */
    fun claimBatch(
        limit: Int,
        now: Instant,
        stuckThresholdSeconds: Long = 600
    ): List<ClaimedRow>

    fun markAsPublished(
        ids: List<Long>,
        now: Instant,
    )

    fun markFailedWithBackoff(
        id: Long,
        cause: String?,
        backoffMillis: Long,
        now: Instant,
    )

}
