pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
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

include("fds-consumer")
project(":fds-consumer").projectDir = file("services/fds/instances/consumer")

include("common-application")
project(":common-application").projectDir = file("common/common-application")

include("common-domain")
project(":common-domain").projectDir = file("common/common-domain")