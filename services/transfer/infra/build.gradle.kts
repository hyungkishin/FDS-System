plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":transfer-domain"))

    implementation("org.springframework.boot:spring-boot-starter")
}
