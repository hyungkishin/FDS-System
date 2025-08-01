plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":transfer-application"))
    implementation(project(":transfer-domain"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("jakarta.persistence:jakarta.persistence-api")
    implementation("org.springframework.boot:spring-boot-starter")
}
