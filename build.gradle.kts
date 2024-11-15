
plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("plugin.spring") version "1.9.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("io.arrow-kt:arrow-core:2.0.0-alpha.4")
    implementation("com.eventstore:db-client-java:5.4.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.0")
    implementation("org.apache.pulsar:pulsar-client:4.0.0")
    implementation("org.apache.pulsar:pulsar-client-admin:4.0.0")
    implementation("org.apache.pulsar:pulsar-client-api:4.0.0")
    implementation("org.apache.pulsar:pulsar-client-admin-api:4.0.0")
    implementation("io.micrometer:micrometer-core:1.13.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:8.5.11")

    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.25.2")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.8.0")
    testImplementation("org.hamcrest:hamcrest-all:1.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:testcontainers:1.17.2")
    testImplementation("org.testcontainers:pulsar:1.20.3")
    testImplementation("org.testcontainers:postgresql:1.20.3")
    testImplementation("io.rest-assured:rest-assured:5.5.0")
    testImplementation("io.rest-assured:json-path:5.5.0")
    testImplementation("javax.servlet:javax.servlet-api:4.0.1")

}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

