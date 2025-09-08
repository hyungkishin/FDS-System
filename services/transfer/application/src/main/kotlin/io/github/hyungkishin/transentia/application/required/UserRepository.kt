package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.domain.model.user.User

interface UserRepository {

    fun findById(id: Long): User?

    fun findByAccountNumber(accountNumber: String): User?

    fun save(user: User): User

}