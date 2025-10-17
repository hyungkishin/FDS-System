pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

// 실행에는 아무 문제 없는것으로 확인.
// Gradle 7.x 이후부터 안정적으로 쓰이고 있고, Gradle 팀도 장려하는 패턴이다 ( API가 변경될 수도 있으니 장기적으로는 주의하라는 의미 )
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    // 레포지토리 선언을 중앙(settings)에서만 관리하고, 모듈에서는 쓰지 않는다 는 전략
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        google()
    }

}

rootProject.name = "transentia"

val services = listOf(
    "transfer",
     "fds"
)

// 계층 모듈 (domain, application, infra)
services.forEach { service ->
    listOf("domain", "application", "infra").forEach { layer ->
        val name = "$service-$layer"
        include(name)
        project(":$name").projectDir = file("services/$service/$layer")
    }
}

// 실행 모듈 (boot/*)
include("transfer-api")
project(":transfer-api").projectDir = file("services/transfer/instances/api")

include("transfer-relay")
project(":transfer-relay").projectDir = file("services/transfer/instances/transfer-relay")

include("fds-api")
project(":fds-api").projectDir = file("services/fds/instances/api")

include("common-application")
project(":common-application").projectDir = file("common/common-application")

include("common-domain")
project(":common-domain").projectDir = file("common/common-domain")

include("kafka-config")
project(":kafka-config").projectDir = file("infrastructure/kafka/kafka-config")

include("kafka-producer")
project(":kafka-producer").projectDir = file("infrastructure/kafka/kafka-producer")

include("kafka-consumer")
project(":kafka-consumer").projectDir = file("infrastructure/kafka/kafka-consumer")

include("kafka-model")
project(":kafka-model").projectDir = file("infrastructure/kafka/kafka-model")
