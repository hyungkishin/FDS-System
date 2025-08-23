package io.github.hyungkishin.transentia.domain.event

import io.github.hyungkishin.transentia.common.snowflake.TransferId
import io.github.hyungkishin.transentia.common.snowflake.UserId
import io.github.hyungkishin.transentia.domain.model.Money
import java.time.LocalDateTime

data class TransferCompletedEvent(
    val transactionId: TransferId,
    val senderUserId: UserId,
    val receiverUserId: UserId,
    val amount: Money,
    val completedAt: LocalDateTime
) : TransferEvent