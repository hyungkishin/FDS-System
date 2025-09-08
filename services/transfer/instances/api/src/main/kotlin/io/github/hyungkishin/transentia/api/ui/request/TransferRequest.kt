package io.github.hyungkishin.transentia.api.ui.request

import io.github.hyungkishin.transentia.application.provided.command.TransferRequestCommand
import jakarta.validation.constraints.Min
import org.jetbrains.annotations.NotNull

data class TransferRequest(
    @field:NotNull val receiverAccountNumber: String,
    @field:Min(1) val amount: String,
    @field:NotNull val message: String,
) {
    fun toCommand(senderUserId: Long): TransferRequestCommand =
        TransferRequestCommand(senderUserId, receiverAccountNumber, amount, message)
}