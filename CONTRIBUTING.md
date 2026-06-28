# Contributing to subscription-service-plugins-community

Thanks for considering a contribution. This repo is a catalog of community plugins for [subscription-service](https://github.com/bzimbelman/subscription-service). A plugin is a Gradle module (Kotlin/JVM) that implements one of the SPIs published in `com.bzonfhir.subscriptionservice:plugins-spi` and is loaded into customers' production deployments via the engine's `ServiceLoader`-based registry.

## Ground rules — the contribution bar

Plugins are loaded into customers' production deployments. We are highly conservative about what we accept.

**Every plugin MUST:**

- Implement at least one SPI published by `com.bzonfhir.subscriptionservice:plugins-spi`. Plugins MUST NOT depend on engine internals (anything outside the SPI artifact). Engine-internal imports are a blocking review finding.
- Be **JVM-only**, written in **Kotlin** or **Java**, targeting **JDK 17** (matching the engine's runtime).
- Pass the schema check on `manifest.yaml` (validated against the schema bundled with the depended-upon `plugins-spi` version).
- Pass `./gradlew :build` cleanly (compiles, all tests pass, no `runtimeOnly` references to undeclared dependencies).
- Ship with unit tests AND at least one integration test that exercises the SPI contract.
- Declare every external dependency in `build.gradle.kts` with a pinned version. No `+`, no `latest.release`.
- Use the `META-INF/services/<spi-interface>` ServiceLoader file to register the implementation. The engine does NOT scan for annotations.
- Document configuration keys in a `config-schema.json` referenced from `manifest.yaml`.

**Plugins MUST NOT:**

- Contain credentials, API keys, tokens, customer-specific URLs, or PHI in tests or fixtures. Use `<placeholder>` style values.
- Include network calls in tests that hit live external services. Use a recorded fixture or an in-process test double of the external transport.
- Ship a shaded "fat" JAR that re-bundles `plugins-spi` or any engine class. Build a thin JAR; declare `plugins-spi` as `compileOnly`.
- Add new SPI surface. New extension points are proposed on the [engine repo](https://github.com/bzimbelman/subscription-service) and land in `plugins-spi` before a plugin here can use them.
- Perform side effects at class-load time (no static blocks that open sockets, read files, or hit env vars). Side effects belong inside SPI lifecycle methods.

**We reserve the right to reject any contribution at our discretion.** Common rejection reasons: scope creep, security concerns, missing tests, dependencies on engine internals, license-incompatible third-party libraries, plugins that try to expand the SPI by stuffing reflection or classpath tricks.

## What we accept

| Kind of PR | Bar |
|---|---|
| **New plugin** | Module builds, manifest validates, ships with unit + integration tests, README documents config + limitations. Plugin module lives under `plugins/<plugin-id>/`. |
| **Update to an existing plugin** | Bumps `plugin.version`, adds tests for new behavior, leaves existing tests green. |
| **Bug fix** | Adds a regression test that fails before the fix and passes after. |
| **Doc / typo** | Open the PR; CI will tell us. |
| **New SPI** | NOT accepted here — propose it on the [engine repo](https://github.com/bzimbelman/subscription-service) first. |
| **Cross-cutting framework changes** | NOT accepted here — open an issue first; many such changes belong in the engine, not the plugin catalog. |

## Workflow

1. **Fork** the repo, clone, create a branch.
2. **Copy the template:** `cp -r community-template/ plugins/<your-plugin-id>/` and start editing.
3. **Implement the SPI** following [docs/authoring-guide.md](docs/authoring-guide.md) and [docs/plugin-types.md](docs/plugin-types.md).
4. **Add tests** under `src/test/kotlin/` — unit tests for logic, integration tests against the SPI contract test kit shipped with `plugins-spi`.
5. **Build locally:**
   ```bash
   cd plugins/<your-plugin-id>
   ./gradlew :build
   ```
6. **Open a PR** using the appropriate template (bug / new-plugin / enhancement).
7. **Review:** at least one maintainer reviews. We may ask for changes; we may say no. We will always tell you why.
8. **Merge:** maintainers merge after CI is green and review is approved.

## Versioning

Each plugin is independently versioned with semantic versioning:

- **MAJOR** (`<plugin-id>-v3.0.0`): breaking changes — config shape changed, SPI version bumped to an incompatible major.
- **MINOR** (`<plugin-id>-v2.1.0`): backward-compatible — new optional config, new behaviors gated behind feature flags.
- **PATCH** (`<plugin-id>-v2.0.1`): backward-compatible fixes — bug fix, dependency bump that doesn't change behavior.

The `plugin.version` field in `manifest.yaml` is the SemVer for THAT plugin. The `contract.spiVersion` field is the `plugins-spi` version the plugin compiled against; it changes when you upgrade the SPI dependency.

## Releases & signing

Tagging a release creates a tarball of just the plugin's source directory (NOT a published Maven artifact):

```bash
git tag <plugin-id>-v<semver>
git push origin <plugin-id>-v<semver>
```

The `release.yml` workflow attaches `<plugin-id>-v<semver>.tgz` to a GitHub Release. Consumers build the JAR locally from the tarball.

**Signing (TODO):** future releases will be signed with [cosign](https://docs.sigstore.dev/cosign/overview/). Consumers will be able to verify the signature before extracting a tarball. Not yet implemented; PRs to add it are welcome.

## Commit messages & sign-off

We use the **Developer Certificate of Origin (DCO)** — sign every commit:

```bash
git commit -s -m "<plugin-id>: add retry-on-429 behavior to delivery loop"
```

No CLA. The Apache 2.0 + DCO combination keeps things simple and contributor-friendly.

Commit message format: `<plugin-id>: <short subject>` (lower-case subject, imperative mood). The body explains *why*.

## Code of conduct

By participating in this project you agree to follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).

## Reporting security issues

See [SECURITY.md](SECURITY.md). **Do not file a public GitHub issue** for a plugin that mishandles data, leaks credentials, or otherwise has security implications.

## Questions

Open a [GitHub Discussion](https://github.com/bzimbelman/subscription-service/discussions) on the engine repo. We use one Discussions instance for the whole project.
