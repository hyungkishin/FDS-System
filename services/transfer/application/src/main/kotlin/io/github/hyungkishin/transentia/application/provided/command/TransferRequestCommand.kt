package io.github.hyungkishin.transentia.application.provided.command

import io.github.hyungkishin.transentia.common.model.Amount
import io.github.hyungkishin.transentia.common.model.Currency
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId

data class TransferRequestCommand(
    val senderId: Long,
    val receiverAccountNumber: String,
    val amount: String,
    val currency: Currency = Currency.KRW,
    val message: String,
) {

    fun senderUserId(): SnowFlakeId = SnowFlakeId(senderId)

    fun receiverAccountNumber(): String = receiverAccountNumber

    fun amount(): Amount = Amount.parse(amount, currency)

}