package io.github.hyungkishin.transentia.application

import io.github.hyungkishin.transentia.application.provided.TransactionRegister
import io.github.hyungkishin.transentia.application.provided.command.TransferRequestCommand
import io.github.hyungkishin.transentia.application.required.AccountBalanceRepository
import io.github.hyungkishin.transentia.application.required.TransactionRepository
import io.github.hyungkishin.transentia.application.required.command.TransferResponseCommand
import io.github.hyungkishin.transentia.application.required.event.EventPublisherPort
import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.model.Transaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val transactionHistoryService: TransactionHistoryService,
    private val idGenerator: IdGenerator,
    private val eventPublisher: EventPublisherPort
) : TransactionRegister {

    @Transactional
    override fun create(command: TransferRequestCommand): TransferResponseCommand {
        val sender = accountBalanceRepository.findByUserId(command.senderUserId())
            ?: throw DomainException(
                CommonError.NotFound("account_balance", command.senderUserId().toString()),
                "송신자 계좌 정보를 찾을 수 없습니다. snowFlakeId=${command.senderUserId()}"
            )

        val receiver = accountBalanceRepository.findByUserId(command.receiverUserId())
            ?: throw DomainException(
                CommonError.NotFound("account_balance", command.receiverUserId().toString()),
                "수신자 계좌 정보를 찾을 수 없습니다. snowFlakeId=${command.receiverUserId()}"
            )

        val transfer = Transaction.of(
            SnowFlakeId(idGenerator.nextId()), sender.snowFlakeId, receiver.snowFlakeId, command.amount()
        )

        try {
            sender.withdrawOrThrow(command.amount())
            receiver.deposit(command.amount())

            accountBalanceRepository.save(sender)
            accountBalanceRepository.save(receiver)

            val saved = transactionRepository.save(transfer)
            transactionHistoryService.recordSuccess(saved)

            eventPublisher.publish(saved.complete())

            return TransferResponseCommand.from(saved)
        } catch (ex: RuntimeException) {
            transactionHistoryService.recordFail(transfer, ex.message)
            eventPublisher.publish(transfer.fail(ex.message ?: "SYSTEM_ERROR"))
            throw ex
        }
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