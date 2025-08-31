package io.github.hyungkishin.transentia.infra.rdb.adapter

import io.github.hyungkishin.transentia.application.required.AccountBalanceRepository
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.model.AccountBalance
import io.github.hyungkishin.transentia.infra.rdb.entity.AccountBalanceJpaEntity
import io.github.hyungkishin.transentia.infra.rdb.repository.AccountBalanceJpaRepository
import org.springframework.stereotype.Component

@Component
class AccountBalancePersistenceAdapter(
    private val jpaRepository: AccountBalanceJpaRepository
) : AccountBalanceRepository {

    override fun findByUserId(snowFlakeId: SnowFlakeId): AccountBalance? =
        jpaRepository.findById(snowFlakeId.value).orElse(null)?.toDomain()

    override fun save(account: AccountBalance): AccountBalance {
        val currentVersion = jpaRepository.findById(account.id.value)
            .map { it.version }
            .orElse(null)

        val entity = AccountBalanceJpaEntity.from(account, currentVersion)

        return jpaRepository.save(entity).toDomain()
    }

}