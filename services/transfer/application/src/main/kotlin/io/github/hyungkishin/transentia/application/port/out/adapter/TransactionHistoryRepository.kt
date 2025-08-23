package io.github.hyungkishin.transentia.application.port.out.adapter

import io.github.hyungkishin.transentia.domain.model.TransactionHistory

interface TransactionHistoryRepository {

    fun save(transactionHistory: TransactionHistory)

}