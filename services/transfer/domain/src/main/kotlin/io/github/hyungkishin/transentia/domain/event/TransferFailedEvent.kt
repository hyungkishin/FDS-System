package io.github.hyungkishin.transentia.domain.event

import io.github.hyungkishin.transentia.common.snowflake.TransferId
import java.time.LocalDateTime

data class TransferFailedEvent(
    val transactionId: TransferId,
    val reason: String,
    val failedAt: LocalDateTime
) : TransferEvent