## What this PR does

<one-paragraph summary>

## Type of change

- [ ] New plugin
- [ ] Enhancement to an existing plugin
- [ ] Bug fix
- [ ] Doc / typo

## Checklist

- [ ] `./gradlew :build` passes from `plugins/<plugin-id>/`
- [ ] `manifest.yaml` declares the correct `contract.spiVersion` and a single SPI under `implements`
- [ ] `META-INF/services/<spi-interface>` ServiceLoader registration is present and points at the implementation class
- [ ] Tests use only obviously-synthetic data (`<placeholder>` style values, no real customer URLs / tokens / PHI)
- [ ] No dependency on engine internals — only `plugins-spi` and declared third-party libs
- [ ] Plugin `version` bumped per SemVer if behavior changed
- [ ] Commits signed with DCO (`git commit -s`)
