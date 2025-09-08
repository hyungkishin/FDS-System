package io.github.hyungkishin.transentia.application

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.application.provided.TransactionRegister
import io.github.hyungkishin.transentia.application.provided.command.TransferRequestCommand
import io.github.hyungkishin.transentia.application.required.TransactionRepository
import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.application.required.command.TransferResponseCommand
import io.github.hyungkishin.transentia.application.required.outbox.TransferEvent
import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.IdGenerator
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.transaction.Transaction
import io.github.hyungkishin.transentia.domain.validator.transfer.TransferValidator
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val userService: UserServiceImpl,
    private val transactionHistoryService: TransactionHistoryService,
    private val idGenerator: IdGenerator,
    private val transferEventsOutboxRepository: TransferEventsOutboxRepository,
    private val objectMapper: ObjectMapper, // 별도 Bean 으로 승격
) : TransactionRegister {

    @Transactional
    override fun create(command: TransferRequestCommand): TransferResponseCommand {

        val (sender, receiver) = userService.findUser(command.senderUserId(), command.receiverAccountNumber())

        TransferValidator.validate(sender, receiver, command.amount())

        val transfer = Transaction.of(
            SnowFlakeId(idGenerator.nextId()), sender.id, receiver.id, command.amount()
        )

        try {
            sender.accountBalance.withdrawOrThrow(command.amount())
            receiver.accountBalance.deposit(command.amount())

            userService.save(sender)
            userService.save(receiver)

            val transaction = transactionRepository.save(transfer)
            transactionHistoryService.recordSuccess(transaction)

            enqueueOutbox(
                eventType = "TRANSFER_COMPLETED",
                aggregateType = "Transaction",
                aggregateId = transaction.id.value,
                payload = mapOf(
                    "eventId" to transaction.id.value,
                    "transactionId" to transaction.id.value,
                    "senderId" to transaction.senderId.value,
                    "receiverId" to transaction.receiverId.value,
                    "amount" to transaction.amount.rawValue,
                    "status" to "COMPLETED",
                    "occurredAt" to System.currentTimeMillis()
                ),
                headers = mapOf(
                    "eventType" to "TransferCompleted",
                    "eventVersion" to "v1",
                    "traceId" to currentTraceId(),
                    "producer" to "transfer-api",
                    "contentType" to "application/json"
                )
            )

            return TransferResponseCommand.from(transaction)
        } catch (ex: RuntimeException) {
            transactionHistoryService.recordFail(transfer, ex.message)
            enqueueOutbox(
                eventType = "TRANSFER_FAILED",
                aggregateType = "Transaction",
                aggregateId = transfer.id.value,
                payload = mapOf(
                    "eventId" to transfer.id.value,
                    "transactionId" to transfer.id.value,
                    "senderId" to transfer.senderId.value,
                    "receiverId" to transfer.receiverId.value,
                    "amount" to transfer.amount.rawValue,
                    "status" to "FAILED",
                    "reason" to (ex.message ?: "SYSTEM_ERROR"),
                    "occurredAt" to System.currentTimeMillis()
                ),
                headers = mapOf(
                    "eventType" to "TransferFailed",
                    "eventVersion" to "v1",
                    "traceId" to currentTraceId(),
                    "producer" to "transfer-api",
                    "contentType" to "application/json"
                )
            )
            throw ex
        }
    }

    /** Outbox에 한 건 적재 */
    // JSON -? ??
    private fun enqueueOutbox(
        eventType: String,
        aggregateType: String,
        aggregateId: Long,
        payload: Map<String, Any?>,
        headers: Map<String, String>
    ) {
        val payloadJson = objectMapper.writeValueAsString(payload.toMutableMap())
        val headersJson = objectMapper.writeValueAsString(headers)

        val row = TransferEvent(
            eventId = idGenerator.nextId(),
            aggregateType = aggregateType,
            aggregateId = aggregateId.toString(),
            eventType = eventType,
            payload = payloadJson,
            headers = headersJson
        )

        transferEventsOutboxRepository.save(row)
    }

    private fun currentTraceId(): String =
        MDC.get("traceId") ?: UUID.randomUUID().toString()

    override fun get(transactionId: Long): TransferResponseCommand {
        val tx = transactionRepository.findById(transactionId)
            ?: throw DomainException(
                CommonError.NotFound("transaction", transactionId.toString()),
                "송금 이력이 존재하지 않습니다. id=$transactionId"
            )
        return TransferResponseCommand.from(tx)
    }

}