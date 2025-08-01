package com.io.github.hyungkishin.transentia

import io.github.hyungkishin.transentia.infra.config.TransferInfraJpaConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@Import(
    TransferInfraJpaConfig::class
)
@SpringBootApplication
class TransferApiApplication

fun main(args: Array<String>) {
    runApplication<TransferApiApplication>(*args)
}
