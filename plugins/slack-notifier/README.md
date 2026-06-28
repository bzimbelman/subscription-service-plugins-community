# slack-notifier

Slack incoming-webhook notifier for [subscription-service](https://github.com/bzimbelman/subscription-service). Posts an alert to a configured Slack channel when the engine's dead-letter queue (DLQ) crosses an operator-defined threshold.

OpenProject ticket: #468 (Epic #429).

## What it does

Polls the engine's `/admin/observe/dlq` admin endpoint on a fixed cadence (60s by default). For each configured `dlq-threshold` rule, when the observed DLQ size exceeds the rule's `threshold`, the plugin formats a Slack [Block Kit](https://api.slack.com/block-kit) message and POSTs it to the configured incoming-webhook URL. Each rule has its own cooldown so a stuck DLQ does not spam Slack on every tick.

## Why a poller (not a `MessageSink`)

The plugins-spi `MessageSink` SPI fires per FHIR Subscription event. That works for "every subscription firing forwards to Slack", but the operator value here is "tell me when things are broken." Polling the public admin DLQ endpoint is decoupled from engine internals and surfaces aggregate health signals (DLQ size) that the per-event SPI does not expose.

The `MessageSink` SPI shape is still available for a future "forward every subscription to Slack" variant of this plugin — see `manifest.yaml`'s `implements` declaration.

## Configuration

```yaml
plugins:
  slack-notifier:
    enabled: true
    webhook-url: "${SLACK_WEBHOOK_URL}"     # REQUIRED, from env
    channel: "#integrations"                # optional override
    admin-base-url: "http://localhost:8080" # engine admin API base
    admin-bearer-token: "${ADMIN_TOKEN}"    # optional, from env
    max-retries: 3
    rules:
      - name: dlq-size
        type: dlq-threshold
        threshold: 10
        cooldown-seconds: 600
```

All values use `<placeholder>` form in committed examples. Never commit a real webhook URL — they grant write access to a Slack channel.

| Key | Required | Default | Purpose |
|---|---|---|---|
| `enabled` | no | `true` | Master toggle. When `false`, no scheduling registers. |
| `webhook-url` | **yes** | — | Slack incoming-webhook URL. Resolve from an env var. |
| `channel` | no | (channel the webhook was created for) | Optional `#channel` override. |
| `admin-base-url` | no | `http://localhost:8080` | Where the engine's admin API lives. |
| `admin-bearer-token` | no | — | Optional bearer token for the admin API. |
| `max-retries` | no | `3` | Additional retries after a Slack 5xx. |
| `rules` | no | (empty) | List of alerting rules. |

Rule fields:

| Key | Required for | Notes |
|---|---|---|
| `name` | all | Used in cooldown bookkeeping and the alert footer. |
| `type` | all | `dlq-threshold` is the only type the initial release evaluates. |
| `threshold` | `dlq-threshold` | Fire when DLQ size > this value. |
| `threshold-percent` | `error-rate` | (reserved) Failure-percentage threshold. |
| `window-minutes` | `error-rate` | (reserved) Rolling-window length. |
| `cooldown-seconds` | all | After firing, suppress further fires for this many seconds. Default `600`. |

## Installation

Build the plugin's thin JAR and drop it on the engine's classpath:

```bash
./gradlew :plugins:slack-notifier:build
# JAR is at plugins/slack-notifier/build/libs/slack-notifier-<version>.jar
```

Mount it into the engine's image at the directory the engine's plugin
loader scans (e.g. `/app/plugins/`). Set `SLACK_WEBHOOK_URL` in the
environment.

## Behavior details

- The poller never throws on a failed admin-endpoint call. It logs at WARN and waits for the next tick.
- The webhook client retries 5xx responses (transient) but NOT 4xx (permanent — a 4xx usually means the webhook URL is wrong, and Slack's API rate-limits aggressively on repeat 4xx).
- Cooldown is per-rule, in-memory. A restart resets cooldown state. That is intentional — a fresh start should re-page if the DLQ is still over threshold.

## Testing

```bash
./gradlew :plugins:slack-notifier:test
```

Tests use WireMock to stand up local HTTP servers for both the Slack webhook endpoint and the engine's admin endpoint. No real Slack URLs or tokens are involved.

## Limitations

- The `error-rate` rule type is parsed but not evaluated. Wire it up by extending `DlqPoller` (or adding a sibling poller) in a future iteration.
- Cooldown state is in-memory; restart clears it.
- The plugin does not deduplicate alerts across multiple engine replicas. If you run more than one engine instance, each will poll independently. Centralizing cooldown in Redis is a follow-up.

## License

Apache 2.0. See the repository's [LICENSE](../../LICENSE).
