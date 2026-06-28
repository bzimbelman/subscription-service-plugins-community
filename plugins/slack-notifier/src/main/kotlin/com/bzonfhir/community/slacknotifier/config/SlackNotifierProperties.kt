package com.bzonfhir.community.slacknotifier.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the slack-notifier community plugin (OpenProject
 * #468). Bound from `application.yaml` under the prefix
 * `plugins.slack-notifier`.
 *
 * Example:
 *
 * ```yaml
 * plugins:
 *   slack-notifier:
 *     enabled: true
 *     webhook-url: "${SLACK_WEBHOOK_URL}"     # required, from env
 *     channel: "#integrations"                # optional
 *     admin-base-url: "http://localhost:8080"
 *     rules:
 *       - name: dlq-size
 *         type: dlq-threshold
 *         threshold: 10
 *         cooldown-seconds: 600
 * ```
 *
 * Mutable `var`s because Spring Boot's relaxed binding writes via
 * setters when binding from YAML.
 */
@ConfigurationProperties(prefix = "plugins.slack-notifier")
data class SlackNotifierProperties(
    /**
     * Master toggle. When `false` the auto-config skips scheduler
     * registration and nothing else in the plugin runs.
     */
    var enabled: Boolean = true,

    /**
     * Slack incoming-webhook URL. Required. Resolve from an env var
     * (`${SLACK_WEBHOOK_URL}`); never commit the literal.
     */
    var webhookUrl: String = "",

    /**
     * Optional `#channel` override. Most workspace configurations
     * ignore this — Slack incoming-webhooks post to the channel the
     * webhook was created for.
     */
    var channel: String? = null,

    /**
     * Base URL of the engine's admin API. The poller calls
     * `${adminBaseUrl}/admin/observe/dlq`. Default assumes the plugin
     * runs in the same JVM as the engine and reaches it over the
     * loopback adapter.
     */
    var adminBaseUrl: String = "http://localhost:8080",

    /**
     * Optional bearer token forwarded as `Authorization: Bearer ...`
     * on admin-API calls. Resolve from an env var.
     */
    var adminBearerToken: String? = null,

    /**
     * Additional attempts after an initial Slack POST failure.
     */
    var maxRetries: Int = 3,

    /**
     * Alerting rules. Each rule decides when to fire.
     */
    var rules: List<Rule> = emptyList(),
) {

    /**
     * One alerting rule. `name`+`type` are required; the other knobs
     * vary by `type`.
     */
    data class Rule(
        var name: String = "",
        var type: String = "",
        var threshold: Int? = null,
        var thresholdPercent: Double? = null,
        var windowMinutes: Int? = null,
        var cooldownSeconds: Long = 600L,
    )
}
