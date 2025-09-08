package io.github.hyungkishin.transentia.domain.validator.transfer

import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.domain.model.account.Money
import io.github.hyungkishin.transentia.domain.model.user.User

object TransferValidator {

    /**
     * 송금 가능성 전체 검증
     * 실패 시 즉시 예외 발생 (fail-fast)
     */
    fun validate(sender: User, receiver: User, amount: Money) {
        validateAmount(amount)
        validateSender(sender, amount)
        validateReceiver(receiver)
    }

    /**
     * 송신자 검증
     */
    fun validateSender(sender: User, amount: Money) {
        // 1. 블랙리스트 체크
        if (sender.isBlacklisted()) {
            throw DomainException(
                CommonError.InvalidArgument("sender_blocked"),
                "송금이 제한된 사용자입니다: ${sender.getBlockReason()}"
            )
        }

        // 2. 일일 한도 체크
        if (sender.validateTransferAmount(amount.rawValue)) {
            throw DomainException(
                CommonError.InvalidArgument("daily_limit_exceeded"),
                "일일 송금 한도(${sender.dailyTransferLimit.value}원)를 초과했습니다"
            )
        }

        // 3. 잔액 체크
        if (!sender.accountBalance.hasEnoughBalance(amount)) {
            throw DomainException(
                CommonError.InvalidArgument("insufficient_balance"),
                "잔액이 부족합니다. 현재 잔액: ${sender.accountBalance.balance}원"
            )
        }
    }

    /**
     * 수신자 검증
     */
    fun validateReceiver(receiver: User) {
        if (!receiver.canReceive()) {
            throw DomainException(
                CommonError.InvalidArgument("receiver_blocked"),
                "입금이 불가능한 계정입니다: ${receiver.getBlockReason()}"
            )
        }

        if (receiver.isBlacklisted()) {
            throw DomainException(
                CommonError.InvalidArgument("user_blocked"),
                "제한된 사용자입니다: ${receiver.getBlockReason()}"
            )
        }
    }

    /**
     * 기본 금액 검증
     */
    fun validateAmount(amount: Money) {
        if (amount.isZero()) {
            throw DomainException(
                CommonError.InvalidArgument("invalid_amount"),
                "송금 금액은 0원보다 커야 합니다"
            )
        }
    }

}