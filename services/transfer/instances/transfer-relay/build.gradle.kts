plugins {
    id("transentia.kotlin-library")
    id("transentia.spring-boot-app")
    id("transentia.kafka-convention")
}

dependencies {
    // 프로젝트 의존성
    implementation(project(":transfer-application"))
    implementation(project(":transfer-infra"))
    implementation(project(":common-application"))
    implementation(project(":common-domain"))
    implementation(project(":kafka-config"))
    implementation(project(":kafka-producer"))
    implementation(project(":kafka-model"))

    // Relay 특화 의존성만
    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

// 성능 테스트는 Relay 특화 기능이므로 유지
tasks.register<Test>("performanceTest") {
    useJUnitPlatform()
    include("**/*PerformanceTest*")
    group = "verification"
    description = "성능 테스트 실행"
}