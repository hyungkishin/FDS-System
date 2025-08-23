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
    // "wallet",
    // "fds"
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
services.forEach { service ->
    listOf("api", "publisher", "consumer").forEach { app ->
        val name = "$service-$app"
        include(name)
        project(":$name").projectDir = file("services/$service/instances/$app")
    }
}

include("delivery-http-error")
project(":delivery-http-error").projectDir = file("common/delivery-http-error")

include("shared-common")
project(":shared-common").projectDir = file("common/shared-common")