package io.github.hyungkishin.transentia.container.model.transaction

import io.github.hyungkishin.transentia.common.message.transfer.TransferCompleted
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.container.enums.TransactionStatus
import io.github.hyungkishin.transentia.container.model.account.Money
import java.time.Clock
import java.time.Instant

class Transaction private constructor(
    val id: SnowFlakeId,
    val senderId: SnowFlakeId,
    val receiverId: SnowFlakeId,
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
                senderId = senderSnowFlakeId,
                receiverId = receiverSnowFlakeId,
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
    fun complete(): TransferCompleted {
        check(status == TransactionStatus.PENDING) { "PENDING 상태만 완료할 수 있습니다." }
        status = TransactionStatus.COMPLETED
        return TransferCompleted(id.value, senderId.value, receiverId.value, amount.rawValue)
    }


    override fun equals(other: Any?): Boolean = other is Transaction && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Transaction(id=$id, sender=$senderId, receiver=$receiverId, amount=$amount, status=$status)"

}
