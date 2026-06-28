// plugins/slack-notifier — the first community plugin (OpenProject #468).
//
// Watches the subscription-service DLQ and posts Slack alerts when the
// DLQ size or error rate crosses an operator-configured threshold.
//
// Architecture: a Spring `@Scheduled` poller against the engine's
// `/admin/observe/dlq` admin endpoint. Decoupled from engine internals —
// the only contract is the published REST API, plus the `plugins-spi`
// types we reuse for plugin identity (`PluginMeta`, `PluginSupplier`).
//
// Bytecode 17 — matches plugins-spi and the engine.
//
// Dependencies follow the same `compileOnly`-heavy pattern the built-in
// plugins use: consumers bring their own Spring Boot + plugins-spi. We
// ship a thin JAR.

plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
}

group = "com.bzonfhir.community"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenLocal()
    mavenCentral()
}

val springBootVersion = "3.2.6"
val pluginsSpiVersion = "0.1.0-SNAPSHOT"
val jacksonVersion = "2.17.2"
val wiremockVersion = "3.9.1"

dependencies {
    // Kotlin standard library.
    api("org.jetbrains.kotlin:kotlin-stdlib")

    // The SPI surface we reuse — PluginMeta, PluginSupplier — for
    // identity. We're not implementing a SinkOutcome-returning interface
    // (the poller approach is decoupled from MessageSink), but we still
    // want the plugin to look like every other plugin in the catalog.
    compileOnly("com.bzonfhir.subscriptionservice:plugins-spi:$pluginsSpiVersion")

    // Spring Boot autoconfig + scheduling. `compileOnly` so the consumer
    // (a customer's subscription-service deployment) supplies the
    // Spring version they actually run on.
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot:$springBootVersion")
    compileOnly("org.springframework:spring-context:6.1.14")
    compileOnly("org.springframework:spring-web:6.1.14")

    // Jackson for parsing the DLQ admin response. Spring Boot already
    // brings Jackson, but we declare it compileOnly so our own
    // compilation succeeds.
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // SLF4J for logging. Spring brings it; we declare compileOnly so
    // we don't pin a version into the thin plugin JAR.
    compileOnly("org.slf4j:slf4j-api:2.0.13")

    // Tests.
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.25.3")

    // Tests need the SPI and Spring on the runtime classpath.
    testImplementation("com.bzonfhir.subscriptionservice:plugins-spi:$pluginsSpiVersion")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-test:$springBootVersion")
    testImplementation("org.springframework:spring-test:6.1.14")
    testImplementation("org.springframework:spring-web:6.1.14")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // WireMock — stand up a fake Slack webhook endpoint AND a fake
    // /admin/observe/dlq endpoint in the poller tests.
    testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")

    // Test-time SLF4J binding so log statements don't NPE in tests.
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
