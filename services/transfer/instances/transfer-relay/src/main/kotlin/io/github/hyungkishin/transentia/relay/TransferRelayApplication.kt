package io.github.hyungkishin.transentia.relay

import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling

@ComponentScan("io.github.hyungkishin.transentia")
@EnableConfigurationProperties(OutboxRelayConfig::class)
@EnableScheduling
@SpringBootApplication
class TransferRelayApplication

fun main(args: Array<String>) {
    runApplication<TransferRelayApplication>(*args)
}
