package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.container.event.TransferEvent
import java.time.Instant

interface TransferEventsOutboxRepository {

    fun save(row: TransferEvent, now: Instant)

    fun claimBatch(
        limit: Int,
        now: Instant,
    ): List<ClaimedRow>

    /**
     * 파티션 기반으로 이벤트 배치를 조회하고 SENDING 상태로 변경한다.
     *
     * 여러 Relay 인스턴스가 동시에 실행되더라도 각자 다른 파티션의 이벤트를 처리하여
     * 락 경합을 줄이고 부하를 분산한다.
     *
     * @param partition 현재 인스턴스의 파티션 ID (0, 1, 2...)
     * @param totalPartitions 전체 인스턴스 수
     * @param limit 한 번에 처리할 최대 이벤트 수
     * @param now 기준 시간 (기본값: 현재 시간, 테스트 시 고정 시간 주입 가능)
     * @return 처리할 이벤트 목록
     */
    fun claimBatchByPartition(
        partition: Int,
        totalPartitions: Int,
        limit: Int,
        now: Instant,
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