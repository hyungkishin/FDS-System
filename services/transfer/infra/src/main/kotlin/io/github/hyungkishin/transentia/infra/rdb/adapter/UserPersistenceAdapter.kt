package io.github.hyungkishin.transentia.infra.rdb.adapter

import io.github.hyungkishin.transentia.application.required.UserRepository
import io.github.hyungkishin.transentia.domain.model.user.User
import io.github.hyungkishin.transentia.infra.rdb.entity.UserJpaEntity
import io.github.hyungkishin.transentia.infra.rdb.repository.UserJpaRepository
import org.springframework.stereotype.Component

@Component
class UserPersistenceAdapter(
    private val jpaRepository: UserJpaRepository
) : UserRepository {

    override fun findById(id: Long): User? =
        jpaRepository.findByIdWithAccount(id)?.toDomain()

    override fun findByAccountNumber(accountNumber: String): User? =
        jpaRepository.findByAccountNumberWithAccountBalances(accountNumber)?.toDomain()

    override fun save(user: User): User {
        // 조회 없이 바로 업데이트 (dirty checking 활용)
        val entity = UserJpaEntity.from(user)
        return jpaRepository.save(entity).toDomain()
    }

}