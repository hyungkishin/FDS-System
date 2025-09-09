package io.github.hyungkishin.transentia.application

import io.github.hyungkishin.transentia.application.provided.TransactionRegister
import io.github.hyungkishin.transentia.application.provided.command.TransferRequestCommand
import io.github.hyungkishin.transentia.application.required.TransactionRepository
import io.github.hyungkishin.transentia.application.required.UserRepository
import io.github.hyungkishin.transentia.application.required.command.TransferResponseCommand
import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.transaction.Transaction
import io.github.hyungkishin.transentia.domain.validator.transfer.TransferValidator
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryService: TransactionHistoryService,
    private val idGenerator: IdGenerator,
    private val eventPublisher: ApplicationEventPublisher,
) : TransactionRegister {

    @Transactional
    override fun create(command: TransferRequestCommand): TransferResponseCommand {

        val sender = userRepository.findById(command.senderId) ?: throw DomainException(
            CommonError.NotFound("account_balance", command.senderId.toString()),
            "송신자 정보를 찾을 수 없습니다. senderId=${command.senderId}"
        )

        val receiver = userRepository.findByAccountNumber(command.receiverAccountNumber) ?: throw DomainException(
            CommonError.NotFound("account_balance", command.receiverAccountNumber.toString()),
            "수신자 계좌 정보를 찾을 수 없습니다. snowFlakeId=${command.receiverAccountNumber}"
        )

        // TODO: 배치 서버 생성 + 비관적 Lock 으로 User 의 일일 송금액 검증 필요
        TransferValidator.validate(sender, receiver, command.amount())

        val transaction = Transaction.of(
            SnowFlakeId(idGenerator.nextId()),
            sender.id,
            receiver.id,
            command.amount()
        )

        try {
            sender.accountBalance.withdrawOrThrow(command.amount())
            receiver.accountBalance.deposit(command.amount())

            userRepository.save(sender)
            userRepository.save(receiver)

            val completeEvent = transaction.complete()

            val transaction = transactionRepository.save(transaction)
            transactionHistoryService.recordSuccess(transaction)

            eventPublisher.publishEvent(completeEvent)

            return TransferResponseCommand.from(transaction)
        } catch (ex: RuntimeException) {
            val failedEvent = transaction.fail(ex.message ?: "SYSTEM_ERROR")
            transactionHistoryService.recordFail(transaction, ex.message)

            eventPublisher.publishEvent(failedEvent)
            throw ex
        }
    }

    override fun findTransaction(transactionId: Long): TransferResponseCommand {
        val tx = transactionRepository.findById(transactionId)
            ?: throw DomainException(
                CommonError.NotFound("transaction", transactionId.toString()),
                "송금 이력이 존재하지 않습니다. id=$transactionId"
            )
        return TransferResponseCommand.from(tx)
    }

}