plugins {
    id("transentia.kotlin-library")
    id("transentia.code-coverage")
}

dependencies {
    implementation(project(":common-domain"))

//    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
//    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
//
//    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
//    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
}
