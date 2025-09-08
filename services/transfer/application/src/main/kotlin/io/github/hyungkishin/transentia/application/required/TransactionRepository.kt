package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.domain.model.transaction.Transaction

interface TransactionRepository {
    fun save(transaction: Transaction): Transaction
    fun findById(id: Long): Transaction?
//    fun findByClientRequestId(id: String): Transaction?
}