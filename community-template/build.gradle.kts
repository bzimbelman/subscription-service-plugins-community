// Community plugin template — copy this module to plugins/<your-plugin-id>/, then edit.
//
// This file intentionally uses <placeholder> values for the plugin id, version,
// and SPI version. Replace them before opening a PR.
//
// See ../docs/authoring-guide.md for the end-to-end walkthrough.

plugins {
    kotlin("jvm") version "1.9.25"
    `java-library`
}

group = "com.bzonfhir.community"
// Replace with your plugin id and version (must match manifest.yaml).
version = "<placeholder-version>"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    // The engine publishes plugins-spi to GitHub Packages. Override
    // GITHUB_PACKAGES_URL / token via environment in CI; see docs/publishing.md.
    maven {
        name = "github-packages-subscription-service"
        url = uri(System.getenv("GITHUB_PACKAGES_URL") ?: "https://maven.pkg.github.com/bzimbelman/subscription-service")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: "<placeholder-actor>"
            password = System.getenv("GITHUB_TOKEN") ?: "<placeholder-token>"
        }
    }
}

dependencies {
    // The SPI contract. compileOnly so the plugin JAR doesn't re-bundle it;
    // the engine provides this on the classpath at load time.
    compileOnly("com.bzonfhir.subscriptionservice:plugins-spi:<placeholder-spi-version>")

    // Your runtime dependencies go here. Pin every version explicitly.
    // Example (replace or remove):
    // implementation("org.slf4j:slf4j-api:2.0.13")

    testImplementation("com.bzonfhir.subscriptionservice:plugins-spi:<placeholder-spi-version>")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.11")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("<placeholder-plugin-id>")
    manifest {
        attributes(
            "Implementation-Title" to "<placeholder-plugin-id>",
            "Implementation-Version" to project.version,
        )
    }
}
