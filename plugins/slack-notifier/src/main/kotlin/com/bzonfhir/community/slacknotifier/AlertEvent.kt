package com.bzonfhir.community.slacknotifier

import java.time.Instant

/**
 * What the slack-notifier plugin emits internally when a rule fires.
 *
 * The poller / rule evaluator builds one of these; the [EventFormatter]
 * turns it into a Slack-shaped payload; the [SlackWebhookClient] POSTs
 * that payload. Keeping the event a value type lets us unit-test each
 * stage in isolation.
 *
 * @property ruleName The `name` from the configured rule. Surfaces in
 *   the Slack message header so operators can see which rule tripped.
 * @property observedAt The instant the rule evaluator made the decision.
 *   The runtime stamps this, not the formatter — so retries with
 *   cooldown still show the original observation time.
 * @property sampleErrors Up to N (caller's choice) recent `last_error`
 *   strings copied from the DLQ response, included in the Slack
 *   message to give operators a head start on triage. May be empty.
 */
sealed class AlertEvent {

    abstract val ruleName: String
    abstract val observedAt: Instant
    abstract val sampleErrors: List<String>

    /**
     * The DLQ-size threshold rule fired: the engine's DLQ has more
     * entries than the rule's `threshold`.
     */
    data class DlqThresholdCrossed(
        override val ruleName: String,
        val threshold: Int,
        val observedSize: Int,
        override val observedAt: Instant,
        override val sampleErrors: List<String>,
    ) : AlertEvent()

    /**
     * The error-rate rule fired: the rolling-window failure percentage
     * exceeds `thresholdPercent`.
     *
     * Reserved for a follow-up iteration. The shape is here so
     * `EventFormatter` can render it; the rule evaluator implementation
     * is not part of the initial slack-notifier release.
     */
    data class ErrorRateCrossed(
        override val ruleName: String,
        val windowMinutes: Int,
        val thresholdPercent: Double,
        val observedPercent: Double,
        override val observedAt: Instant,
        override val sampleErrors: List<String>,
    ) : AlertEvent()
}
