plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":transfer-application"))
    implementation(project(":transfer-infra"))
    implementation(project(":common-application"))
    implementation(project(":common-domain"))
    implementation(project(":kafka-config"))
    implementation(project(":kafka-producer"))
    implementation(project(":kafka-model"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.kafka:spring-kafka")

    implementation("io.confluent:kafka-avro-serializer:7.9.2")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
