rootProject.name = "transentia"

val services = listOf(
    "transfer",
//    "wallet",
//    "fds"
)

services.forEach { service ->
    listOf("domain", "application", "infra", "api").forEach { layer ->
        val name = "$service-$layer"
        include(name)
        project(":$name").projectDir = file("services/$service/$layer")
    }
}


// 공통 유틸
include("common")
project(":common").projectDir = file("common")
