package io.github.hyungkishin.transentia.container

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["io.github.hyungkishin.transentia"]
)
class FdsApiApplication

fun main(args: Array<String>) {
    runApplication<FdsApiApplication>(*args)
}