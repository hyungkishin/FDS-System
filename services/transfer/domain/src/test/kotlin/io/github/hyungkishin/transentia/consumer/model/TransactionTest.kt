package io.github.hyungkishin.transentia.consumer.model

import io.github.hyungkishin.transentia.common.snowflake.TransferId
import io.github.hyungkishin.transentia.common.snowflake.UserId
import io.github.hyungkishin.transentia.consumer.enums.TransactionStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TransactionTest {

    // private val txId = Snowflake(1L).nextId()
    private val sender = UserId(1L)
    private val receiver = UserId(2L)
    private val amount = Money.fromDecimalString("100.00")

    @Test
    fun `Transaction 요청 시 PENDING 상태로 생성된다`() {
        val tx = Transaction.of(TransferId(100L), sender, receiver, amount)

        assertEquals(TransactionStatus.PENDING, tx.status)
        assertEquals(sender, tx.senderUserId)
        assertEquals(receiver, tx.receiverUserId)
        assertEquals(amount, tx.amount)
        assertNotNull(tx.createdAt)
    }

    @Test
    fun `송신자와 수신자가 같으면 예외가 발생한다`() {
        assertThrows(IllegalArgumentException::class.java) {
            Transaction.of(TransferId(101L), sender, sender, amount)
        }
    }

    @Test
    fun `금액이 0원이면 예외가 발생한다`() {
        val zeroAmount = Money.fromDecimalString("0.00")
        assertThrows(IllegalArgumentException::class.java) {
            Transaction.of(TransferId(102L), sender, receiver, zeroAmount)
        }
    }

    @Test
    fun `PENDING 상태의 트랜잭션은 COMPLETE 상태로 변경될 수 있다`() {
        val tx = Transaction.of(TransferId(103L), sender, receiver, amount)
        tx.complete()

        assertEquals(TransactionStatus.COMPLETED, tx.status)
    }

    @Test
    fun `COMPLETED 상태의 트랜잭션은 다시 COMPLETE 처리할 수 없다`() {
        val tx = Transaction.of(TransferId(104L), sender, receiver, amount)
        tx.complete()

        assertThrows(IllegalStateException::class.java) {
            tx.complete()
        }
    }

    @Test
    fun `PENDING 상태의 트랜잭션은 FAIL 처리할 수 있다`() {
        val tx = Transaction.of(TransferId(105L), sender, receiver, amount)

        assertEquals(TransactionStatus.FAILED, tx.status)
    }

}

