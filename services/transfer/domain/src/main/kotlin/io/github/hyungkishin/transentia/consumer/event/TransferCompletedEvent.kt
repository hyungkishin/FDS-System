package io.github.hyungkishin.transentia.consumer.event

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.model.Money

data class TransferCompletedEvent(
    val snowFlakeId: SnowFlakeId,
    val senderSnowFlakeId: SnowFlakeId,
    val receiverSnowFlakeId: SnowFlakeId,
    val amount: Money,
) : TransferEvent