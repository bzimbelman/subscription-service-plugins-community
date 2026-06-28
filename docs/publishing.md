# Publishing a plugin

After a plugin lands on main, a maintainer tags + releases it. End-users download release tarballs and build the JAR locally; we do NOT publish JARs to Maven Central (yet — see TODO).

## Release flow

```bash
# From main, after the PR merges
git pull
git tag <plugin-id>-v<semver>           # e.g., slack-notifier-v0.1.0
git push origin <plugin-id>-v<semver>
```

The `release.yml` workflow:

1. Parses `<plugin-id>` and `<semver>` from the tag
2. Verifies `plugins/<plugin-id>/` exists at the tagged commit
3. Bundles `plugins/<plugin-id>/` into `<plugin-id>-v<semver>.tgz`
4. Creates a GitHub Release with auto-generated notes from PR titles since the previous release tag for that plugin

The release page is the public artifact. Consumers download the tarball, untar, and run `./gradlew :build` to produce the JAR.

## Versioning rules

Per-plugin SemVer, independent across plugins:

- **MAJOR** — breaking config or behavior: a customer's deployment may need to change config or downstream wiring
- **MINOR** — backward-compatible: new optional config, new behavior gated behind flags, new SPI capability your plugin opted into
- **PATCH** — backward-compatible fix: bug fix, dependency bump that doesn't change behavior

Examples:

- `slack-notifier-v0.1.0` — initial Slack notifier
- `slack-notifier-v0.1.1` — fixed 429 retry logic
- `slack-notifier-v0.2.0` — added blocks-format payload support
- `slack-notifier-v1.0.0` — breaking config rename, recompiled against `plugins-spi` v2
- `s3-archive-v0.1.0-rc.1` — first S3 archive pre-release

## SPI compatibility

The plugin's `contract.spiVersion` in `manifest.yaml` MUST match the `plugins-spi` version your `build.gradle.kts` depends on. The engine validates at load time:

- If the plugin's `spiVersion` is incompatible with the engine's loaded SPI (different MAJOR), the engine refuses to load the plugin and logs a clear error.
- If the `spiVersion` is older but compatible (same MAJOR, lower MINOR), the plugin loads with a warning. Bump it eventually.

A plugin compiled against `plugins-spi:2.x` will NOT work on an engine that loaded `plugins-spi:1.x`. Major bumps require coordinated releases.

## Cosign signing (future)

The release workflow has a TODO marker for cosign signing. When it lands:

1. Each release tarball gets a `.sig` and `.crt` attached
2. Consumers verify with `cosign verify-blob --certificate ...`
3. The engine's plugin loader can be configured to require signatures before loading

This is on the roadmap; not yet enforced. Plain tarballs are accepted today.

## Publishing to Maven Central (future)

We may publish community plugins as Maven Central artifacts in a future iteration so consumers can `implementation("com.bzonfhir.community:<plugin-id>:<version>")` instead of building from source. Not yet implemented. Track on the engine repo's roadmap.

## Yanking a release

If a release has a security issue or correctness bug too severe to live with:

1. Delete the release on GitHub (this hides the tarball download)
2. DO NOT delete the tag — that breaks anyone who pinned to it
3. Tag the fix as a PATCH or MAJOR depending on severity
4. Edit the release's body to point users at the new release

## Pre-release tags

For staged rollout:

```bash
git tag <plugin-id>-v1.0.0-rc.1
git push origin <plugin-id>-v1.0.0-rc.1
```

The workflow detects the `-rc.N` suffix and marks the GitHub Release as a pre-release. Pre-release tarballs are visible but not surfaced as "Latest."
