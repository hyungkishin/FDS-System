package io.github.hyungkishin.transentia.application.port.`in`.services

import io.github.hyungkishin.transentia.domain.model.Transaction

interface TransactionHistoryService {

    fun recordSuccess(transaction: Transaction)

    fun recordFail(transaction: Transaction, reason: String?)

}