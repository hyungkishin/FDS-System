package io.github.hyungkishin.transentia.container.model.transaction

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.container.model.account.Money

class TransactionHistory private constructor(
    val id: SnowFlakeId,
    val transactionId: SnowFlakeId,
    val senderId: SnowFlakeId,
    val receiverId: SnowFlakeId,
    val amount: Money,
    val reason: String?,
) {
    companion object {
        fun of(transaction: Transaction, id: SnowFlakeId): TransactionHistory =
            TransactionHistory(
                id = id,
                transactionId = transaction.id,
                senderId = transaction.senderId,
                receiverId = transaction.receiverId,
                amount = transaction.amount,
                reason = null,
            )
    }
}
