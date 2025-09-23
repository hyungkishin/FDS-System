package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.domain.event.TransferEvent

interface TransferEventsOutboxRepository {

    fun save(row: TransferEvent)

    fun claimBatch(limit: Int): List<ClaimedRow>

    fun markAsPublished(ids: List<Long>)

    fun markFailedWithBackoff(id: Long, cause: String?, backoffMillis: Long)

}