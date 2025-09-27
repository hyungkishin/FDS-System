package io.github.hyungkishin.transentia.container.model.account

import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId

class AccountBalance private constructor(
    val id: SnowFlakeId,
    val userId: SnowFlakeId,
    val accountNumber: String,
    var balance: Money,
    val version: Long,
) {

    companion object {
        fun of(id: SnowFlakeId, userId: SnowFlakeId, accountNumber: String, balance: Money, version: Long): AccountBalance {
            return AccountBalance(id, userId, accountNumber, balance, version)
        }
    }

    fun deposit(amount: Money) {
        balance = balance.add(amount)
    }

    /** 출금, 실패 시 도메인 예외 */
    fun withdrawOrThrow(amount: Money) {
        if (balance < amount) {
            throw DomainException(
                CommonError.InvalidArgument(field = "amount", reason = "insufficient_balance"),
                detail = "잔액이 부족합니다. 현재잔액=$balance, 요청금액=$amount"
            )
        }
        balance = balance.subtract(amount)
    }

    fun current(): Money = balance

    fun hasEnoughBalance(amount: Money): Boolean {
        return balance >= amount
    }

}