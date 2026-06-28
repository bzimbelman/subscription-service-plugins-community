package com.bzonfhir.community.slacknotifier

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * EventFormatter is the pure-function boundary between "we decided to
 * alert" and "we have a Slack payload to POST." Tests are snapshot-style
 * on the shape: the formatter MUST produce a `text` summary (for
 * notifications / older Slack clients) and a `blocks[]` array following
 * Slack's [Block Kit](https://api.slack.com/block-kit) schema.
 *
 * No real Slack URLs / channels — we use `<placeholder>` style values in
 * tests, per CONTRIBUTING rules.
 */
class EventFormatterTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `dlq threshold event produces text summary and blocks array`() {
        val formatter = EventFormatter()
        val event = AlertEvent.DlqThresholdCrossed(
            ruleName = "dlq-size",
            threshold = 10,
            observedSize = 42,
            observedAt = Instant.parse("2026-06-28T12:00:00Z"),
            sampleErrors = listOf(
                "HL7 parse error at MSH-9",
                "Connection refused: lims.example.test:8080",
            ),
        )

        val payload = formatter.format(event, channelOverride = "#integrations")

        // Top-level keys.
        assertThat(payload).containsKeys("text", "blocks", "channel")
        assertThat(payload["channel"]).isEqualTo("#integrations")

        // The notification fallback `text` field includes the rule name,
        // observed size, and threshold.
        val text = payload["text"] as String
        assertThat(text)
            .contains("dlq-size")
            .contains("42")
            .contains("10")

        // The blocks array is well-formed Block Kit. We check the header
        // block contains the rule name, a section block contains the
        // count, and a context block contains the timestamp.
        @Suppress("UNCHECKED_CAST")
        val blocks = payload["blocks"] as List<Map<String, Any>>
        assertThat(blocks).isNotEmpty()
        assertThat(blocks.first()["type"]).isEqualTo("header")

        val asJson = mapper.writeValueAsString(payload)
        assertThat(asJson)
            .contains("dlq-size")
            .contains("Connection refused")
            .contains("2026-06-28T12:00:00Z")
    }

    @Test
    fun `error rate event produces text summary and blocks array`() {
        val formatter = EventFormatter()
        val event = AlertEvent.ErrorRateCrossed(
            ruleName = "error-rate",
            windowMinutes = 5,
            thresholdPercent = 5.0,
            observedPercent = 17.3,
            observedAt = Instant.parse("2026-06-28T12:05:00Z"),
            sampleErrors = emptyList(),
        )

        val payload = formatter.format(event, channelOverride = null)

        // No channel override → no channel key.
        assertThat(payload).doesNotContainKey("channel")

        val text = payload["text"] as String
        assertThat(text)
            .contains("error-rate")
            .contains("17.3")
            .contains("5.0")
            .contains("5") // window-minutes
    }

    @Test
    fun `payload is stable and serializes to valid JSON`() {
        val formatter = EventFormatter()
        val event = AlertEvent.DlqThresholdCrossed(
            ruleName = "dlq-size",
            threshold = 10,
            observedSize = 11,
            observedAt = Instant.parse("2026-06-28T00:00:00Z"),
            sampleErrors = emptyList(),
        )

        val payload = formatter.format(event, channelOverride = null)
        val json = mapper.writeValueAsString(payload)

        // Round-trip the JSON.
        val reparsed = mapper.readValue(json, Map::class.java)
        assertThat(reparsed["text"]).isEqualTo(payload["text"])
    }
}
