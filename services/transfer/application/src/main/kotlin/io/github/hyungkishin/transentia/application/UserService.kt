package io.github.hyungkishin.transentia.application

import io.github.hyungkishin.transentia.application.provided.UserService
import io.github.hyungkishin.transentia.application.required.UserRepository
import io.github.hyungkishin.transentia.common.error.CommonError
import io.github.hyungkishin.transentia.common.error.DomainException
import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserServiceImpl(
    private val userRepository: UserRepository
) : UserService {

    @Transactional(readOnly = true)
    override fun findUser(senderId: SnowFlakeId, receiverAccountNumber: String): Pair<User, User> {
        val sender = userRepository.findById(senderId.value) ?: throw DomainException(
            CommonError.NotFound("account_balance", senderId.toString()),
            "송신자 정보를 찾을 수 없습니다. senderId=${senderId}"
        )

        val receiver = userRepository.findByAccountNumber(receiverAccountNumber) ?: throw DomainException(
            CommonError.NotFound("account_balance", receiverAccountNumber.toString()),
            "수신자 계좌 정보를 찾을 수 없습니다. snowFlakeId=${receiverAccountNumber}"
        )

        return Pair(sender, receiver)
    }

    override fun save(user: User) {
        userRepository.save(user)
    }

}