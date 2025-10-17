package io.github.hyungkishin.transentia.infra.rdb.repository

import io.github.hyungkishin.transentia.infra.rdb.entity.UserJpaEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserJpaRepository: JpaRepository<UserJpaEntity, Long> {

    @Query(
        """
            SELECT u 
            FROM UserJpaEntity u 
            LEFT JOIN FETCH u.account 
            WHERE u.id = :id
        """
    )
    fun findByIdWithAccount(@Param("id") id: Long): UserJpaEntity?

    /**
     * 계좌번호로 User + Account 조회
     */
    @Query(
        """
            SELECT u 
            FROM UserJpaEntity u 
            LEFT JOIN FETCH u.account a 
            WHERE a.accountNumber = :accountNumber
        """
    )
    fun findByAccountNumberWithAccountBalances(@Param("accountNumber") accountNumber: String): UserJpaEntity?

}