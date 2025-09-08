package io.github.hyungkishin.transentia.domain.event

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId

data class TransferFailedEvent(
    val snowFlakeId: SnowFlakeId,
    val reason: String,
) : TransferEvent