package io.github.hyungkishin.transentia.consumer.event

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId

data class TransferFailedEvent(
    val snowFlakeId: SnowFlakeId,
    val reason: String,
) : TransferEvent