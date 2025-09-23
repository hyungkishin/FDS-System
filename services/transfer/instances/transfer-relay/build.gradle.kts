plugins {
    id("transentia.spring-boot-app")
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

    // 외부 의존성 (Convention Plugin에서 공통으로 처리되지 않는 것들만)
    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.kafka:spring-kafka")

    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance")
    }
}

// 별도 성능 테스트 태스크
tasks.register<Test>("performanceTest") {
    useJUnitPlatform()
    include("**/*PerformanceTest*")
    group = "verification"
    description = "성능 테스트 실행"
}