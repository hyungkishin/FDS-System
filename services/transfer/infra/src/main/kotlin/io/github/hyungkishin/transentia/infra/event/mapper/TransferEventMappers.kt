package io.github.hyungkishin.transentia.infra.event.mapper

import io.github.hyungkishin.transentia.common.message.transfer.TransferCompletedV1
import io.github.hyungkishin.transentia.common.message.transfer.TransferFailedV1
import io.github.hyungkishin.transentia.domain.event.TransferCompletedEvent
import io.github.hyungkishin.transentia.domain.event.TransferFailedEvent

fun TransferCompletedEvent.toContract() = TransferCompletedV1(
    txId = snowFlakeId.value,
    senderUserId = senderSnowFlakeId.value,
    receiverUserId = receiverSnowFlakeId.value,
    amountMinor = amount.rawValue,
)

fun TransferFailedEvent.toContract() = TransferFailedV1(
    txId = snowFlakeId.value,
    reason = reason
)