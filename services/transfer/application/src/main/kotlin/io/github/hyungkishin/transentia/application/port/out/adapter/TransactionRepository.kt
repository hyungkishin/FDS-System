package io.github.hyungkishin.transentia.application.port.out.adapter

import io.github.hyungkishin.transentia.domain.model.Transaction

interface TransactionRepository {
    fun save(transaction: Transaction): Transaction
    fun findById(id: Long): Transaction?
//    fun findByClientRequestId(id: String): Transaction?
}