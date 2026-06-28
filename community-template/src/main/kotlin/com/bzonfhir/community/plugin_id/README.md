# Plugin implementation source

Your plugin's Kotlin (or Java) source files live in this package.

The directory name `plugin_id` is a placeholder. Rename it to match your plugin id with dashes converted to underscores or removed (Kotlin/JVM package segments cannot contain dashes). For example:

- `slack-notifier` -> `com.bzonfhir.community.slack_notifier` -> directory: `slack_notifier/`
- `s3archive` -> `com.bzonfhir.community.s3archive` -> directory: `s3archive/`

## Minimum scaffolding

Every plugin needs at least:

1. An implementation class implementing the SPI declared in `../../../../../../manifest.yaml`.
2. A `ServiceLoader` registration file at:
   `src/main/resources/META-INF/services/<fully-qualified-spi-interface>`
   containing one line: the fully-qualified name of your implementation class.

The engine's plugin registry uses `java.util.ServiceLoader` to discover implementations. There is no annotation-driven discovery.

## Example shape

```kotlin
package com.bzonfhir.community.<plugin_id>

import com.bzonfhir.subscriptionservice.plugins.spi.MessageSink
// ... import the SPI you implement

class <PluginName>Sink : MessageSink {
    override fun id(): String = "<placeholder-plugin-id>"

    override fun deliver(message: OutboundMessage, ctx: SinkContext): DeliveryResult {
        TODO("implement")
    }
}
```

Then in `src/main/resources/META-INF/services/com.bzonfhir.subscriptionservice.plugins.spi.MessageSink`:

```
com.bzonfhir.community.<plugin_id>.<PluginName>Sink
```

See `../../../../../docs/authoring-guide.md` for the full walkthrough.
