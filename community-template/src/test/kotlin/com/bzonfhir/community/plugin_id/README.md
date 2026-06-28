# Plugin tests

Your plugin's tests live in this package. Mirror the source package — if your implementation is in `com.bzonfhir.community.slack_notifier`, your tests are in `src/test/kotlin/com/bzonfhir/community/slack_notifier/`.

## What we expect

- **Unit tests** for your logic. Test pure functions and class behaviors without standing up the engine.
- **At least one integration test** that exercises your implementation against the SPI contract test kit shipped with `plugins-spi`. The contract kit provides standard scenarios (good path, error path, lifecycle ordering); failing any contract scenario is a blocking review finding.

## Synthetic data only

Test fixtures MUST use obviously-synthetic values:

- URLs: `https://<placeholder>.example.invalid/`
- Tokens: `<placeholder-token>`
- Patient ids: `MRN-EXAMPLE-001`
- Names: `Test Patient One`

Real customer URLs, real tokens, or anything resembling PHI is a blocking review finding.

## No live network

Tests MUST NOT hit live external services. Use:

- An in-process test double of the external transport (e.g., `MockWebServer` for HTTP)
- A recorded fixture loaded from `src/test/resources/`
- The SPI contract kit's provided fakes

If your plugin's only meaningful behavior is "talk to a real external service," document that limitation in the plugin README and stick to unit tests of the request-construction logic.
