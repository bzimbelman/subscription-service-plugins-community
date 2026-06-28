# Plugin types

This catalog accepts plugins that implement SPIs published in `com.bzonfhir.subscriptionservice:plugins-spi`. The authoritative interface list lives in that artifact; this doc summarizes the extension-friendly surface.

## Currently supported SPIs

### `SubscriptionFilter`

**Purpose:** decide whether an inbound message should be delivered to a particular subscriber. Returns ACCEPT / REJECT / DEFER with a reason code.

**Common use cases:**

- Route-by-facility (deliver to subscriber X only if `MSH-4` matches a configured list)
- Allow-list patient cohorts (deliver only if the patient is in a research panel)
- Suppress test messages (reject messages where a `MSH-11` processing-id signals non-production)

**Lifecycle:** stateless invocation per (message, subscriber) pair. Implementations MUST be thread-safe.

### `MessageSink`

**Purpose:** deliver an outbound message to an external destination. The engine's routing layer hands the sink a fully-resolved outbound payload plus delivery context.

**Common use cases:**

- Slack notifier (the first community plugin — see ticket #468)
- S3 archive (write every emitted resource to a bucket for retention)
- Custom REST webhook
- Syslog forwarder

**Lifecycle:** the engine constructs one sink instance per configured destination and reuses it; `deliver()` is called repeatedly. Sinks SHOULD implement `close()` to release connections at shutdown.

### `ObservabilityEnricher`

**Purpose:** attach extra metadata to emitted `AuditEvent` resources, log lines, or metric tags. Lets operators inject context (tenant id, trace id, deployment region) without modifying the engine.

**Common use cases:**

- Tag every audit event with the tenant id resolved from a request header
- Propagate W3C trace context from inbound message into outbound emit
- Add a `source-system` label to every metric the engine publishes for this plugin's path

**Lifecycle:** invoked synchronously on the emit path. MUST be fast (single-digit milliseconds) and MUST NOT block on network I/O.

### `IngestSource`

**Purpose:** provide an additional inbound surface beyond the built-in HL7v2 and FHIR ones. The engine treats your source as just another ingest path; messages your source emits flow through the same routing, filter, and emit pipeline as any other.

**Common use cases:**

- Vendor-proprietary REST poller
- File-watch ingester (monitor a directory, drop in messages as they appear)
- SFTP drop processor

**Lifecycle:** the engine calls `start(ctx)` at boot and `close()` at shutdown. Your source is responsible for its own threading and backpressure; it pushes messages via `ctx.emit(message)`.

### `Transformer`

**Purpose:** run a declarative pre/post transformation between ingest and routing. Distinct from FHIR StructureMaps (which are author-time declarative); a `Transformer` plugin runs imperatively over an in-flight message.

**Common use cases:**

- Header normalizer (rewrite vendor-specific header values to a canonical set)
- PHI redactor for non-production environments
- Custom enum mapper for codes the StructureMap layer doesn't yet handle

**Lifecycle:** stateless invocation per message. Implementations MUST be thread-safe and MUST NOT mutate the input message in place — return a new one.

## SPIs we do NOT accept here

These extension points exist but are reserved for the commercial Pro-tier catalog:

- `LicenseGate` — controls feature flags by license
- `TenantResolver` — pluggable multi-tenancy lookup
- `CertifiedConnector` — vendor-certified, signed connectors

If your idea bumps into one of these, it doesn't belong in the community catalog. Open an issue on the engine repo for the maintainers to discuss.

## Proposing a new SPI

If you need an extension point that doesn't exist:

1. Open an issue on the [engine repo](https://github.com/bzimbelman/subscription-service) describing the use case.
2. Iterate with maintainers on the proposed interface shape.
3. The SPI lands in `plugins-spi` first.
4. Once published, a plugin here can implement it.

We do not invent new SPI surface inside the plugins repo. Plugins are consumers of the published contract.
