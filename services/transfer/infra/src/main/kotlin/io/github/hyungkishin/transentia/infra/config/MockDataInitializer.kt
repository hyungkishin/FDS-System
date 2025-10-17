package io.github.hyungkishin.transentia.infra.config

import io.github.hyungkishin.transentia.application.required.UserRepository
import io.github.hyungkishin.transentia.common.model.Amount
import io.github.hyungkishin.transentia.common.model.Currency
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.container.model.user.*
import io.github.hyungkishin.transentia.container.model.account.*
import io.github.hyungkishin.transentia.container.enums.UserStatus
import io.github.hyungkishin.transentia.container.enums.UserRole
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MockDataInitializer(
    private val userRepository: UserRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (userRepository.findById(10001) == null) {

            // Mock User 1 (송신자) - 1만원
            val senderAccount = AccountBalance.of(
                SnowFlakeId(20001),
                SnowFlakeId(10001),
                "110-123-456789",
                Amount.parse("100000", Currency.KRW),
                0,
            )

            val sender = User.of(
                id = SnowFlakeId(10001),
                name = UserName("홍길동"),
                email = Email("sender@test.com"),
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                accountBalance = senderAccount,
                isTransferLocked = false,
                transferLockReason = null,
                dailyTransferLimit = DailyTransferLimit.basic(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            // Mock User 2 (수신자) - 5만원
            val receiverAccount = AccountBalance.of(
                SnowFlakeId(20002),
                SnowFlakeId(10002),
                "110-987-654321",
                Amount.parse("100000", Currency.KRW),
                0
            )

            val receiver = User.of(
                id = SnowFlakeId(10002),
                name = UserName("김철수"),
                email = Email("receiver@test.com"),
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                accountBalance = receiverAccount,
                isTransferLocked = false,
                transferLockReason = null,
                dailyTransferLimit = DailyTransferLimit.basic(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            // Mock User 3 (블랙리스트) - 테스트용
            val blacklistAccount = AccountBalance.of(
                SnowFlakeId(20003),
                SnowFlakeId(10003),
                "110-111-222333",
                Amount.parse("100000", Currency.KRW),
                0
            )

            val blacklistUser = User.of(
                id = SnowFlakeId(10003),
                name = UserName("이영희"),
                email = Email("blacklist@test.com"),
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                accountBalance = blacklistAccount,
                isTransferLocked = true,
                transferLockReason = TransferLockReason("테스트용 제재"),
                dailyTransferLimit = DailyTransferLimit.basic(),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            userRepository.save(sender)
            userRepository.save(receiver)
            userRepository.save(blacklistUser)

            println("Mock 사용자 데이터 초기화 완료")
            println("- 송신자(10001): 1만원, 계좌번호: 110-123-456789")
            println("- 수신자(10002): 5만원, 계좌번호: 110-987-654321")
            println("- 블랙리스트(10003): 10만원, 계좌번호: 110-111-222333 (송금제한)")
        }
    }
}
