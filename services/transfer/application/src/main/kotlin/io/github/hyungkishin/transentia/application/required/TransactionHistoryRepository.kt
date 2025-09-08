package io.github.hyungkishin.transentia.application.required

import io.github.hyungkishin.transentia.domain.model.transaction.TransactionHistory

interface TransactionHistoryRepository {

    fun save(transactionHistory: TransactionHistory)

}