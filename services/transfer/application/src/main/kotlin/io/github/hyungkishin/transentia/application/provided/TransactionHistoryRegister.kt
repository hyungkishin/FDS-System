package io.github.hyungkishin.transentia.application.provided

import io.github.hyungkishin.transentia.domain.model.transaction.Transaction

interface TransactionHistoryRegister {

    fun recordSuccess(transaction: Transaction)

    fun recordFail(transaction: Transaction, reason: String?)

}