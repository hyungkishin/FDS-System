package io.github.hyungkishin.transentia.application.provided

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.user.User

interface UserService {

    fun findUser(senderId: SnowFlakeId, receiverAccountNumber: String): Pair<User, User>

    fun save(user: User)

}