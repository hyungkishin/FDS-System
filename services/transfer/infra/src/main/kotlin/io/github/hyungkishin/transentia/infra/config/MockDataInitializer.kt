package io.github.hyungkishin.transentia.infra.config

import io.github.hyungkishin.transentia.application.required.AccountBalanceRepository
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.model.AccountBalance
import io.github.hyungkishin.transentia.consumer.model.Money
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

/**
 * FIXME: Auth Domain 생성 이후 삭제
 */
@Component
class MockDataInitializer(
    private val accountBalanceRepository: AccountBalanceRepository
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        if (accountBalanceRepository.findByUserId(SnowFlakeId(10001)) == null) {

            // = 1000원
            // = 5만원
            val sender = AccountBalance.of(
                SnowFlakeId(10001),
                SnowFlakeId(10001),
                Money.fromDecimalString("10000.00000000")
            )
            val receiver = AccountBalance.of(
                SnowFlakeId(10002),
                SnowFlakeId(10002),
                Money.fromDecimalString("50000.00000000")
            )

            accountBalanceRepository.save(sender)
            accountBalanceRepository.save(receiver)

            println("Mock 사용자 금액 초기화")
        }
    }
}