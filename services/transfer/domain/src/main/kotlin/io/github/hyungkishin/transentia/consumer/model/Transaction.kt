package io.github.hyungkishin.transentia.consumer.model

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.enums.TransactionStatus
import io.github.hyungkishin.transentia.consumer.event.TransferCompletedEvent
import io.github.hyungkishin.transentia.consumer.event.TransferFailedEvent
import java.time.Clock
import java.time.Instant

class Transaction private constructor(
    val id: SnowFlakeId,
    val senderSnowFlakeId: SnowFlakeId,
    val receiverSnowFlakeId: SnowFlakeId,
    val amount: Money,
    var status: TransactionStatus,
    var createdAt: Instant,
    var failReason: String? = null,
) {

    companion object {
        fun of(
            id: SnowFlakeId,
            senderSnowFlakeId: SnowFlakeId,
            receiverSnowFlakeId: SnowFlakeId,
            amount: Money,
            clock: Clock = Clock.systemUTC()
        ): Transaction {
            require(senderSnowFlakeId != receiverSnowFlakeId) { "송신자와 수신자는 동일할 수 없습니다." }
            require(amount.isPositive()) { "송금 금액은 0보다 커야 합니다." }
            return Transaction(
                id = id,
                senderSnowFlakeId = senderSnowFlakeId,
                receiverSnowFlakeId = receiverSnowFlakeId,
                amount = amount,
                status = TransactionStatus.PENDING,
                createdAt = Instant.now(clock)
            )
        }

        // 복구/재생성용(이벤트 소싱 or 리스토어 시)
        fun restored(
            id: SnowFlakeId,
            senderSnowFlakeId: SnowFlakeId,
            receiverSnowFlakeId: SnowFlakeId,
            amount: Money,
            status: TransactionStatus,
            createdAt: Instant,
            failReason: String?
        ) = Transaction(id, senderSnowFlakeId, receiverSnowFlakeId, amount, status, createdAt, failReason)
    }

    /** 멱등 완료: 이미 COMPLETED면 이벤트 생성 안 함(null) */
    fun complete(): TransferCompletedEvent {
//        check(status == TransactionStatus.COMPLETED) { "이미 종료된 송금입니다." }
        check(status == TransactionStatus.PENDING) { "PENDING 상태만 완료할 수 있습니다." }
        status = TransactionStatus.COMPLETED
        return TransferCompletedEvent(id, senderSnowFlakeId, receiverSnowFlakeId, amount)
    }

    /** 멱등 실패 */
    fun fail(reason: String): TransferFailedEvent {
//        check(status == TransactionStatus.FAILED) { "이미 실패된 송금입니다." }
        check(status == TransactionStatus.PENDING) { "PENDING 상태만 실패 처리할 수 있습니다." }
        status = TransactionStatus.FAILED
        failReason = reason
        return TransferFailedEvent(id, reason)
    }

    override fun equals(other: Any?): Boolean = other is Transaction && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Transaction(id=$id, sender=$senderSnowFlakeId, receiver=$receiverSnowFlakeId, amount=$amount, status=$status)"

}
