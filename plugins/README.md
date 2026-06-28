# Community plugins

Each subdirectory under `plugins/` is one community plugin: a self-contained Gradle module (Kotlin/JVM 17) implementing an SPI from `com.bzonfhir.subscriptionservice:plugins-spi`.

At scaffold time (ticket #467) this directory is empty. Community contributions land here. The first one — `slack-notifier` — is in flight as a separate ticket.

## Naming

- Lower-case-kebab, ASCII letters / digits / dashes only
- The directory name MUST match `plugin.id` in `manifest.yaml`

## Adding a plugin

See [../CONTRIBUTING.md](../CONTRIBUTING.md) and [../docs/authoring-guide.md](../docs/authoring-guide.md). The short version:

```bash
cp -r ../community-template/ ./<your-plugin-id>/
cd <your-plugin-id>
# edit build.gradle.kts and manifest.yaml; implement the SPI; add tests
./gradlew :build
```
