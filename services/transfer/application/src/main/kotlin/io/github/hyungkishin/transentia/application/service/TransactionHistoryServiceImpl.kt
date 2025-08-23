package io.github.hyungkishin.transentia.application.service


import io.github.hyungkishin.transentia.application.port.`in`.services.TransactionHistoryService
import io.github.hyungkishin.transentia.application.port.out.adapter.TransactionHistoryRepository
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.common.snowflake.TransactionId
import io.github.hyungkishin.transentia.domain.model.Transaction
import io.github.hyungkishin.transentia.domain.model.TransactionHistory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionHistoryServiceImpl(
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val idGenerator: IdGenerator,
) : TransactionHistoryService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordSuccess(transaction: Transaction) {
        val history = TransactionHistory.successOf(transaction, TransactionId(idGenerator.nextId()))
        transactionHistoryRepository.save(history)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun recordFail(transaction: Transaction, reason: String?) {
        val history = TransactionHistory.failOf(transaction, TransactionId(idGenerator.nextId()), reason)
        transactionHistoryRepository.save(history)
    }

}