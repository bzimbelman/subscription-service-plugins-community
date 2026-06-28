package com.bzonfhir.community.slacknotifier

/**
 * Turns an [AlertEvent] into a Slack-shaped payload (a `Map<String, Any>`
 * we hand to Jackson to serialize at POST time).
 *
 * Slack's [incoming-webhook contract](https://api.slack.com/messaging/webhooks)
 * accepts either a simple `{ "text": "..." }` body OR a richer payload
 * with a `blocks[]` array following [Block Kit](https://api.slack.com/block-kit).
 * We send BOTH:
 *   - `text` is the fallback (used in notifications and by older clients).
 *   - `blocks[]` renders nicely in the channel.
 *
 * No I/O here — the formatter is a pure function, trivially unit-testable.
 */
class EventFormatter {

    /**
     * Build the JSON-ready payload map.
     *
     * @param event Which alert tripped.
     * @param channelOverride Optional `#channel` to set on the payload.
     *   Most Slack incoming-webhooks ignore this and post to the channel
     *   the webhook was created for, but we still forward it because
     *   some workspace configurations honor it.
     */
    fun format(event: AlertEvent, channelOverride: String?): Map<String, Any> {
        val text = renderText(event)
        val blocks = renderBlocks(event)

        val payload = mutableMapOf<String, Any>(
            "text" to text,
            "blocks" to blocks,
        )
        if (channelOverride != null) {
            payload["channel"] = channelOverride
        }
        return payload
    }

    private fun renderText(event: AlertEvent): String = when (event) {
        is AlertEvent.DlqThresholdCrossed ->
            "[slack-notifier] rule \"${event.ruleName}\" fired: DLQ size " +
                "${event.observedSize} exceeds threshold ${event.threshold}"
        is AlertEvent.ErrorRateCrossed ->
            "[slack-notifier] rule \"${event.ruleName}\" fired: error rate " +
                "${"%.1f".format(event.observedPercent)}% over the last " +
                "${event.windowMinutes}m exceeds threshold " +
                "${"%.1f".format(event.thresholdPercent)}%"
    }

    private fun renderBlocks(event: AlertEvent): List<Map<String, Any>> {
        val blocks = mutableListOf<Map<String, Any>>()

        // Header — operator can see at a glance which rule tripped.
        blocks.add(
            mapOf(
                "type" to "header",
                "text" to mapOf(
                    "type" to "plain_text",
                    "text" to "subscription-service alert: ${event.ruleName}",
                ),
            ),
        )

        // Main section — the body copy.
        blocks.add(
            mapOf(
                "type" to "section",
                "text" to mapOf(
                    "type" to "mrkdwn",
                    "text" to body(event),
                ),
            ),
        )

        // Sample errors, when we have any.
        if (event.sampleErrors.isNotEmpty()) {
            val samples = event.sampleErrors.joinToString(separator = "\n") { "• `$it`" }
            blocks.add(
                mapOf(
                    "type" to "section",
                    "text" to mapOf(
                        "type" to "mrkdwn",
                        "text" to "*Recent errors:*\n$samples",
                    ),
                ),
            )
        }

        // Footer / context with the timestamp.
        blocks.add(
            mapOf(
                "type" to "context",
                "elements" to listOf(
                    mapOf(
                        "type" to "mrkdwn",
                        "text" to "observed at ${event.observedAt}",
                    ),
                ),
            ),
        )

        return blocks
    }

    private fun body(event: AlertEvent): String = when (event) {
        is AlertEvent.DlqThresholdCrossed ->
            "*DLQ size:* ${event.observedSize}\n*Threshold:* ${event.threshold}"
        is AlertEvent.ErrorRateCrossed ->
            "*Error rate:* ${"%.1f".format(event.observedPercent)}%\n" +
                "*Threshold:* ${"%.1f".format(event.thresholdPercent)}%\n" +
                "*Window:* ${event.windowMinutes}m"
    }
}
