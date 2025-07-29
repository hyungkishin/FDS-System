plugins {
    id("org.springframework.boot") apply false // root 에서 false로 설정한 경우 유지
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation(project(":common"))
    implementation(project(":transfer-application"))
}
