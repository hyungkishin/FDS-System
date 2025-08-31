package io.github.hyungkishin.transentia.consumer.model

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.enums.TransactionHistoryStatus

class TransactionHistory private constructor(
    val id: SnowFlakeId,
    val transactionId: SnowFlakeId,
    val senderId: SnowFlakeId,
    val receiverId: SnowFlakeId,
    val amount: Money,
    val status: TransactionHistoryStatus,
    val reason: String?,
) {
    companion object {
        fun successOf(transaction: Transaction, id: SnowFlakeId): TransactionHistory =
            TransactionHistory(
                id = id,
                transactionId = transaction.id,
                senderId = transaction.senderId,
                receiverId = transaction.receiverId,
                amount = transaction.amount,
                status = TransactionHistoryStatus.SUCCESS,
                reason = null,
            )

        fun failOf(transaction: Transaction, id: SnowFlakeId, reason: String?): TransactionHistory =
            TransactionHistory(
                id = id,
                transactionId = transaction.id,
                senderId = transaction.senderId,
                receiverId = transaction.receiverId,
                amount = transaction.amount,
                status = TransactionHistoryStatus.FAIL,
                reason = reason,
            )
    }
}
