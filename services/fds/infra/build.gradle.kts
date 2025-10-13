plugins {
    id("transentia.spring-library")
    id("transentia.spring-jpa")
    id("transentia.kafka-convention")
    id("transentia.code-coverage")
}

dependencies {
    implementation(project(":fds-application"))
    implementation(project(":fds-domain"))
    implementation(project(":common-domain"))
    implementation(project(":kafka-consumer"))
    implementation(project(":kafka-model"))

    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    implementation("org.apache.avro:avro:1.11.4")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.7.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}