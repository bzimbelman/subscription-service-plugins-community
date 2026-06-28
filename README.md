# subscription-service-plugins-community

Community plugins for [subscription-service](https://github.com/bzimbelman/subscription-service) — JVM modules that extend the pipeline by implementing one of the published `plugins-spi` Service Provider Interfaces.

License: Apache 2.0.

## What this repo IS

A **catalog of community plugins**. Each subdirectory under `plugins/` is one plugin: a self-contained Gradle module (Kotlin/JVM 17) that implements one or more SPIs from `com.bzonfhir.subscriptionservice:plugins-spi` and ships as a JAR the engine loads at startup via its `ServiceLoader`-based registry.

A plugin extends the pipeline without forking the engine. It does NOT replace pipeline code; it plugs into well-defined extension points. The set of supported extension points is fixed by the SPI module — plugins MUST NOT depend on engine internals.

The repo holds:

- `plugins/` — one Gradle module per community plugin (empty at scaffold time; populated by subsequent stories)
- `community-template/` — a working skeleton a contributor copies to start a new plugin
- `docs/` — authoring guide, plugin-types reference, publishing flow, testing guide
- `.github/workflows/ci.yml` — per-plugin build/test on every PR via a matrix discovered from `plugins/*/build.gradle.kts`
- `.github/workflows/release.yml` — tags `<plugin-id>-v<semver>` produce a tarball release of the plugin's source directory

## What this repo is NOT

- It is NOT the engine. Pipeline code lives in [bzimbelman/subscription-service](https://github.com/bzimbelman/subscription-service).
- It is NOT the SPI. The contracts are published from the engine as the `plugins-spi` artifact; plugins here are consumers of that artifact.
- It is NOT a marketplace. There's no payment, no install button, no remote registry. To consume a plugin you download a release tarball (or clone) and drop the built JAR into your subscription-service deployment's plugin directory.
- It is NOT where vendor-certified plugins live. The Pro-tier certified plugins ship in a separate commercial repo. The community-quality plugins for the most common extension needs live here, are Apache 2.0, and stay free forever.

## What kinds of plugins this catalog accepts

Plugins MUST implement one (and only one, per module) of the SPIs published in `com.bzonfhir.subscriptionservice:plugins-spi`. The currently supported SPIs:

| SPI | Purpose | Common examples |
|---|---|---|
| `SubscriptionFilter` | Decide whether an inbound message should be delivered to a subscriber | route-by-facility, allow-list patient cohorts, suppress test messages |
| `MessageSink` | Deliver an outbound message to an external destination | Slack notifier, S3 archive, custom REST webhook, syslog forwarder |
| `ObservabilityEnricher` | Attach extra metadata to emitted `AuditEvent` / log lines / metric tags | inject tenant id, propagate trace context, tag by message-source |
| `IngestSource` | Provide an additional inbound surface beyond built-in HL7v2/FHIR | vendor-proprietary REST poller, file-watch ingester, SFTP drop |
| `Transformer` | Run a declarative pre/post transformation between ingest and routing | header normalizer, PHI redactor for non-production, custom enum mapper |

The authoritative list is whatever `plugins-spi` exposes at the version a plugin depends on; this table is informational. If the SPI you need does not exist, file an issue on the engine repo proposing a new extension point. We do NOT accept plugins that bypass the SPI and reach into engine internals.

## How plugins work

Every plugin is a Gradle module with this shape:

```
plugins/<plugin-id>/
  build.gradle.kts                # Kotlin/JVM 17, depends on plugins-spi
  manifest.yaml                   # id, supplier, contract version, SPI implemented
  src/main/kotlin/...             # the SPI implementation
  src/main/resources/META-INF/services/<fully-qualified-spi-interface>
                                  # the ServiceLoader registration
  src/test/kotlin/...             # unit + integration tests
  README.md                       # what the plugin does, configuration, limitations
```

A representative manifest:

```yaml
plugin:
  id: <placeholder-plugin-id>
  version: "<placeholder-version>"
  schemaVersion: 1
  supplier: community
  contract:
    spiArtifact: com.bzonfhir.subscriptionservice:plugins-spi
    spiVersion: "<placeholder-spi-version>"
  implements:
    - <fully.qualified.SpiInterface>
  jvm:
    minVersion: "17"
  config:
    # JSON Schema fragment describing this plugin's accepted config keys
    schema: config-schema.json
```

The engine reads `manifest.yaml` at plugin discovery, validates the declared `spiVersion` is compatible with the engine's loaded SPI, and only then enables the implementation via `ServiceLoader`.

## How to consume a plugin

Two options:

**1. Drop the JAR into the engine's plugin directory.**

```bash
# Download the release tarball
curl -L -o slack-notifier.tgz \
  https://github.com/bzimbelman/subscription-service-plugins-community/releases/download/<plugin-id>-v<placeholder-version>/<plugin-id>-v<placeholder-version>.tgz

# Untar; build; mount the JAR
tar xzf slack-notifier.tgz
cd plugins/<plugin-id>
./gradlew :build
docker run \
  -v "$PWD/build/libs/<plugin-id>-<placeholder-version>.jar:/app/plugins/<plugin-id>.jar" \
  ghcr.io/bzimbelman/subscription-service:latest
```

**2. Build from source.**

```bash
git clone https://github.com/bzimbelman/subscription-service-plugins-community
cd subscription-service-plugins-community/plugins/<plugin-id>
./gradlew :build
```

## How to contribute

See [CONTRIBUTING.md](CONTRIBUTING.md). The short version:

1. Copy `community-template/` to `plugins/<your-plugin-id>/`.
2. Fill in `manifest.yaml`, implement the SPI under `src/main/kotlin/`, register it in `META-INF/services/`.
3. Add tests under `src/test/kotlin/`.
4. Run `./gradlew :build` locally.
5. Open a PR. CI builds and tests every plugin in `plugins/`.

## Repository status

This is the foundational scaffold (OpenProject ticket #467). The first community plugin (`slack-notifier`, ticket #468) lands separately.

## Related links

- Engine: <https://github.com/bzimbelman/subscription-service>
- SPI module: published from the engine as `com.bzonfhir.subscriptionservice:plugins-spi`
- Sister repo (vendor profiles): <https://github.com/bzimbelman/subscription-service-profiles>
- Issue tracker: GitHub Issues on this repo
- Community discussion: GitHub Discussions on the engine repo
