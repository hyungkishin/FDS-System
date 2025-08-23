package io.github.hyungkishin.transentia.application.port.`in`.services

import io.github.hyungkishin.transentia.application.port.`in`.command.TransferRequestCommand
import io.github.hyungkishin.transentia.application.port.out.command.TransferResponseCommand

interface TransferService {
    fun create(command: TransferRequestCommand): TransferResponseCommand

    fun get(transactionId: Long): TransferResponseCommand
}