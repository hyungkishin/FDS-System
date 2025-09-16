plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":common-application"))
    implementation(project(":common-domain"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")

    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-core")
}
