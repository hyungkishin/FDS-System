package io.github.hyungkishin.transentia.application.provided.command

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.account.Money

data class TransferRequestCommand(
    val senderId: Long,
    val receiverAccountNumber: String,
    val amount: String,
    val message: String,
) {
    fun senderUserId(): SnowFlakeId = SnowFlakeId(senderId)
    fun receiverAccountNumber(): String = receiverAccountNumber
    fun amount(): Money = Money.fromDecimalString(amount)
}