plugins {
    id("transentia.spring-boot-app")
}

dependencies {
    implementation(project(":fds-application"))
    implementation(project(":fds-infra"))
    implementation(project(":common-application"))
    implementation(project(":common-domain"))

    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.flywaydb:flyway-core")

    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}