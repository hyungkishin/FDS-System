plugins {
    id("transentia.spring-library")
    id("transentia.spring-jpa")
    id("transentia.kafka-convention")
    id("transentia.code-coverage")
}

dependencies {
    implementation(project(":transfer-application"))
    implementation(project(":transfer-domain"))
    implementation(project(":common-domain"))
    implementation(project(":kafka-producer"))
    implementation(project(":kafka-model"))

    implementation("io.confluent:kafka-avro-serializer:7.9.2")
}
