# Testing a plugin

## What CI runs

For every PR and every push to `main`, `.github/workflows/ci.yml`:

1. Discovers every `plugins/<id>/build.gradle.kts` in the repo
2. Spawns one matrix job per discovered plugin
3. Each job runs `./gradlew :build` from inside the plugin's directory

`./gradlew :build` compiles, runs unit + integration tests, and produces the JAR under `build/libs/`. A test failure or compile error fails the job; that plugin's PR is blocked from merge.

## What you should run locally before opening a PR

```bash
cd plugins/<your-plugin-id>
./gradlew :build
```

If `:build` passes, CI will pass.

For faster iteration during development:

```bash
./gradlew :test               # tests only
./gradlew :compileKotlin      # compile main source only
./gradlew :compileTestKotlin  # compile test source only
```

## Required test layers

### Unit tests

Test pure functions and class behaviors without booting the engine. Use:

- JUnit 5 (`org.junit.jupiter`)
- `io.mockk` for Kotlin mocks (or `org.mockito` if you're writing Java)
- Test fixtures loaded from `src/test/resources/`

### SPI contract tests

The `plugins-spi` artifact ships a contract test kit (jar classifier `tests`). Depend on it as `testImplementation`, extend the kit's base class for your SPI, and the kit exercises your plugin against a standard set of scenarios (good path, error paths, lifecycle ordering).

Failing any contract scenario is a blocking review finding. The kit is the same one the engine's built-in plugins are verified against; passing it is the bar for "this plugin behaves like a citizen of the pipeline."

### Integration tests (optional but encouraged)

If your plugin has meaningful interaction with an external transport, write an integration test that uses an in-process double:

- HTTP: `okhttp3.mockwebserver.MockWebServer`
- AWS: `software.amazon.awssdk:s3:.*` configured against a local stub (testcontainers/localstack is allowed for non-CI runs; CI defaults to in-process doubles to keep runs fast)
- File I/O: a `Files.createTempDirectory` test root

Live external services in tests are NOT allowed. CI runs in an offline-friendly environment.

## Synthetic data

Test fixtures MUST use obviously-synthetic values. See `CONTRIBUTING.md` for the bar. Reviewers reject anything that looks like real customer data.

## End-to-end against the engine (manual)

Before tagging a release, exercise the plugin against a real subscription-service deployment:

```bash
# 1. Build the JAR
cd plugins/<your-plugin-id>
./gradlew :build

# 2. Mount it into the engine
cd /path/to/subscription-service/deploy/docker
docker compose --env-file .env up -d
docker cp \
  /path/to/subscription-service-plugins-community/plugins/<your-plugin-id>/build/libs/<your-plugin-id>-<placeholder-version>.jar \
  subscription-service-interface-engine:/app/plugins/<your-plugin-id>.jar
docker compose restart interface-engine

# 3. Verify the engine loaded the plugin
docker compose logs interface-engine | grep -i 'loaded plugin <your-plugin-id>'

# 4. Exercise the relevant path (depends on the SPI you implement)
```

This is not yet enforced in CI — when ticket #475 lands, we'll have an end-to-end harness similar to the profiles repo's ticket #446. Until then, manual verification is the bar before tagging.
