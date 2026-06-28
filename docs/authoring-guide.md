# Authoring a community plugin

End-to-end recipe for contributing a new plugin. Estimated time: a few hours for a simple `MessageSink`, more for an `IngestSource` or anything stateful.

## Prerequisites

- JDK 17 installed locally.
- Gradle 8.x (the wrapper inside each plugin module pins this; you don't need a system install).
- A working local subscription-service deployment for end-to-end testing. Follow the [engine repo's quickstart](https://github.com/bzimbelman/subscription-service#quickstart).
- The [plugins-spi reference docs](https://github.com/bzimbelman/subscription-service/tree/main/plugins-spi) so you know which SPI to implement.
- `git` and a text editor.

## Step 1 — Pick an SPI

Browse `docs/plugin-types.md`. Decide which extension point you're plugging into. One plugin module implements one SPI in almost every case; if you find yourself wanting to implement two, prefer splitting into two modules.

If you need an extension point that doesn't exist yet: file an issue on the [engine repo](https://github.com/bzimbelman/subscription-service) proposing it. The SPI lands there first; only then does a plugin here implement it.

## Step 2 — Copy the template

```bash
git clone https://github.com/bzimbelman/subscription-service-plugins-community
cd subscription-service-plugins-community
cp -r community-template/ plugins/<your-plugin-id>/
```

`<your-plugin-id>` should be a lower-case-kebab id: `slack-notifier`, `s3-archive`, `syslog-forwarder`, etc. Stick to ASCII letters, digits, and dashes. No underscores; no dots.

## Step 3 — Edit `build.gradle.kts`

In `plugins/<your-plugin-id>/build.gradle.kts`:

1. Set `version` to the SemVer for the initial release (e.g., `"0.1.0"`).
2. Set `archiveBaseName` in the `jar` task to your plugin id.
3. Pin the `plugins-spi` version to the latest compatible release.
4. Add any third-party dependencies you need; pin every version.

## Step 4 — Edit `manifest.yaml`

Replace every `<placeholder>`:

- `plugin.id` — your plugin id (must match the directory name)
- `plugin.version` — must match `version` in `build.gradle.kts`
- `plugin.contract.spiVersion` — must match the `plugins-spi` version your build depends on
- `plugin.implements` — fully-qualified SPI interface name(s)
- `plugin.config.schema` — relative path to a `config-schema.json` you add alongside the manifest

## Step 5 — Implement the SPI

Drop your implementation under `src/main/kotlin/com/bzonfhir/community/<plugin_id>/`. Use `<plugin_id>` with dashes converted to underscores (Kotlin packages can't contain dashes).

Then register the implementation:

```
src/main/resources/META-INF/services/<fully-qualified-spi-interface>
```

This file contains one line: the fully-qualified name of your implementation class. The engine's plugin registry calls `ServiceLoader.load(SpiInterface::class.java)` to discover the implementation. Annotation scanning is NOT supported.

## Step 6 — Add tests

Under `src/test/kotlin/com/bzonfhir/community/<plugin_id>/`:

- Unit tests for any non-trivial logic
- At least one integration test that uses the SPI contract test kit from `plugins-spi:<version>:tests` (jvm test fixtures classifier). The contract kit exercises every plugin in a uniform way; passing it is required.

Synthetic data only. See `CONTRIBUTING.md` for the full bar.

## Step 7 — Build locally

```bash
cd plugins/<your-plugin-id>
./gradlew :build
```

This compiles, runs tests, and packages the JAR under `build/libs/`. CI runs the same command.

## Step 8 — Open a PR

```bash
git checkout -b <your-plugin-id>-initial
git add plugins/<your-plugin-id>/
git commit -s -m "<your-plugin-id>: initial plugin"
git push origin <your-plugin-id>-initial
gh pr create --template=new-plugin.md
```

The `-s` flag signs the commit per the DCO requirement.

## Step 9 — Review

At least one maintainer reviews. We may ask for changes; we may say no. We will always tell you why.

Common review comments:

- "Your plugin imports an engine internal — only depend on `plugins-spi`."
- "Your `META-INF/services/` registration is missing; the engine can't discover the implementation."
- "Your tests hit a live network. Use a recorded fixture or in-process double."
- "Your manifest's `spiVersion` doesn't match the version your build depends on."

## Step 10 — Release

After merge, a maintainer tags `<your-plugin-id>-v<semver>` (e.g., `slack-notifier-v0.1.0`). The release workflow bundles the plugin's source directory and attaches a tarball to a GitHub Release. Consumers build the JAR locally from the tarball.

Subsequent updates follow the same flow — edit + build + PR. Bump `version` in both `build.gradle.kts` and `manifest.yaml` per SemVer.
