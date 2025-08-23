package io.github.hyungkishin.transentia.application.service

import io.github.hyungkishin.transentia.application.port.`in`.command.TransferRequestCommand
import io.github.hyungkishin.transentia.application.port.`in`.services.TransferService
import io.github.hyungkishin.transentia.application.port.out.adapter.AccountBalanceRepository
import io.github.hyungkishin.transentia.application.port.out.adapter.TransactionRepository
import io.github.hyungkishin.transentia.application.port.out.command.TransferResponseCommand
import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.common.snowflake.TransferId
import io.github.hyungkishin.transentia.domain.model.Transaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransferServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionHistoryServiceImpl: TransactionHistoryServiceImpl,
    private val idGenerator: IdGenerator,
) : TransferService {

    @Transactional
    override fun create(command: TransferRequestCommand): TransferResponseCommand {
        val sender = accountBalanceRepository.findByUserId(command.senderUserId())
            ?: throw DomainException(
                CommonError.NotFound("account_balance", command.senderUserId().toString()),
                "송신자 계좌 정보를 찾을 수 없습니다. userId=${command.senderUserId()}"
            )

        val receiver = accountBalanceRepository.findByUserId(command.receiverUserId())
            ?: throw DomainException(
                CommonError.NotFound("account_balance", command.receiverUserId().toString()),
                "수신자 계좌 정보를 찾을 수 없습니다. userId=${command.receiverUserId()}"
            )

        // Transaction 애그리거트 생성 (초기 상태 = PENDING)
        val transfer = Transaction.start(
            TransferId(idGenerator.nextId()), sender.userId, receiver.userId, command.amount()
        )

        try {
            sender.withdrawOrThrow(command.amount())
            receiver.deposit(command.amount())

            accountBalanceRepository.save(sender)
            accountBalanceRepository.save(receiver)

            transfer.complete()
            transactionHistoryServiceImpl.recordSuccess(transfer)

        } catch (ex: Exception) {
            transfer.fail(ex.message ?: "UNKNOWN_ERROR")
            transactionHistoryServiceImpl.recordFail(transfer, ex.message)
            // publisher.publish(transfer.toEvent())
            throw ex
        }


        val saved = transactionRepository.save(transfer)
        // publisher.publish(transfer.toEvent())
        return TransferResponseCommand.from(saved)
    }

    override fun get(transactionId: Long): TransferResponseCommand {
        val tx = transactionRepository.findById(transactionId)
            ?: throw DomainException(
                CommonError.NotFound("transaction", transactionId.toString()),
                "송금 이력이 존재하지 않습니다. id=$transactionId"
            )
        return TransferResponseCommand.from(tx)
    }
}
