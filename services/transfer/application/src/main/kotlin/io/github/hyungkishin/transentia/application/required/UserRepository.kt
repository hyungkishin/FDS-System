package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.container.model.user.User

interface UserRepository {

    fun findById(id: Long): User?

    fun findByAccountNumber(accountNumber: String): User?

    fun save(user: User): User

}