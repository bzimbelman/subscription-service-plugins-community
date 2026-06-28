# Community plugin template

Copy this directory to `plugins/<your-plugin-id>/` (lower-case-kebab), then:

1. **Pick an SPI.** Decide which interface from `com.bzonfhir.subscriptionservice:plugins-spi` you'll implement. See `../docs/plugin-types.md` for the catalog.
2. **Edit `manifest.yaml`.** Replace every `<placeholder>` with a real value. See `../docs/authoring-guide.md` for what each field means.
3. **Implement the SPI** under `src/main/kotlin/com/bzonfhir/community/<plugin-id>/`. Rename the package segment from `<plugin-id>` to your plugin id (with dashes converted to underscores or removed).
4. **Register the implementation** by creating `src/main/resources/META-INF/services/<fully-qualified-spi-interface>` containing the fully-qualified name of your implementation class (one per line).
5. **Add tests** under `src/test/kotlin/com/bzonfhir/community/<plugin-id>/`. At minimum: one unit test for your logic, one integration test against the SPI's contract test kit.
6. **Build locally:**
   ```bash
   cd plugins/<your-plugin-id>
   ./gradlew :build
   ```
7. Open a PR using the new-plugin template.

See `../CONTRIBUTING.md` for the contribution bar and `../docs/authoring-guide.md` for the end-to-end walkthrough.

## What this directory contains

- `build.gradle.kts` — Kotlin/JVM 17 build, depends on `plugins-spi` as `compileOnly`
- `manifest.yaml` — placeholder metadata; replace before opening a PR
- `src/main/kotlin/com/bzonfhir/community/<plugin-id>/README.md` — placeholder; your implementation source lives here
- `src/test/kotlin/com/bzonfhir/community/<plugin-id>/README.md` — placeholder; your tests live here

## What this directory does NOT contain

- `settings.gradle.kts` — each plugin is a single-module Gradle build; no settings file is needed when you build the module directly with `./gradlew :build` from inside the plugin directory.
- A `META-INF/services/` registration — you must add this for your specific SPI.
- A `config-schema.json` — required if your plugin accepts configuration; reference it from `manifest.yaml`.
