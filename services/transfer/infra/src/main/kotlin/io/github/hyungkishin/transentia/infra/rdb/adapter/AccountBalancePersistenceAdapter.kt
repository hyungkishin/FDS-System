package io.github.hyungkishin.transentia.infra.rdb.adapter

import io.github.hyungkishin.transentia.application.required.AccountBalanceRepository
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.account.AccountBalance
import io.github.hyungkishin.transentia.infra.rdb.entity.AccountBalanceJpaEntity
import io.github.hyungkishin.transentia.infra.rdb.repository.AccountBalanceJpaRepository
import org.springframework.stereotype.Component

@Component
class AccountBalancePersistenceAdapter(
    private val jpaRepository: AccountBalanceJpaRepository
) : AccountBalanceRepository {

    override fun findByUserId(userId: SnowFlakeId): AccountBalance? =
        jpaRepository.findById(userId.value).orElse(null)?.toDomain()

    override fun save(account: AccountBalance): AccountBalance {
        val entity = AccountBalanceJpaEntity.from(account)

        return jpaRepository.save(entity).toDomain()
    }

}