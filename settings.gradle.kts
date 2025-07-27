rootProject.name = "fds-system"

listOf(
    "common",
    "api-server",
    "infra-rdb",
    "core-transfer"
    // auth, core-transfer, infra-kafka, infra-redis, infra-rdb, txworker, syncworker, detector
).forEach {
    include(it)
    project(":$it").projectDir = File("$rootDir/service/$it")
}