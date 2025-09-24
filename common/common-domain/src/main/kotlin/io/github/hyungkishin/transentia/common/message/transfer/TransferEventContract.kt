package io.github.hyungkishin.transentia.common.message.transfer

import java.time.Instant

// 송금시, 공용 이벤트 계약 ( - 원시 타입만 wrapping 하지 않는다. )
sealed interface TransferEventContract {
    val occurredAt: Instant
    val transactionId: Long
}

data class TransferCompleted(
    override val transactionId: Long,
    val senderUserId: Long,
    val receiverUserId: Long,
    val amount: Long, // Money 객체 rawValue (scale = 8)
    override val occurredAt: Instant = Instant.now(),
) : TransferEventContract

data class TransferFailed(
    override val transactionId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
) : TransferEventContract