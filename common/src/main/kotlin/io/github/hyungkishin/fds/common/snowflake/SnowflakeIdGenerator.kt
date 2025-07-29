package io.github.hyungkishin.fds.common.snowflake

class SnowflakeIdGenerator(
    private val snowflake: Snowflake
) {
    fun generateTransferId(): TransferId = TransferId(snowflake.nextId())
    fun generateUserId(): UserId = UserId(snowflake.nextId())
}