package com.bzonfhir.community.slacknotifier

import com.bzonfhir.community.slacknotifier.config.SlackNotifierProperties
import com.bzonfhir.subscriptionservice.spi.meta.PluginMeta
import com.bzonfhir.subscriptionservice.spi.meta.PluginSupplier
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/**
 * Spring auto-configuration for the slack-notifier community plugin
 * (OpenProject #468).
 *
 * Wires up two beans:
 *
 *   1. [SlackWebhookClient] — the HTTP client that POSTs to Slack.
 *   2. [DlqPollerScheduler] — Spring component carrying a `@Scheduled`
 *      method that drives [DlqPoller.tick] on a fixed cadence.
 *
 * Plugin identity is exposed via [meta] for the operator UI's
 * "plugins-loaded" listing.
 *
 * Gates:
 *
 *   - Master toggle `plugins.slack-notifier.enabled` (default `true`).
 *     Flip off to disable scheduling without removing the JAR.
 *   - `plugins.slack-notifier.webhook-url` MUST be set. The poller
 *     short-circuits if it is empty.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "plugins.slack-notifier",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableConfigurationProperties(SlackNotifierProperties::class)
@EnableScheduling
open class SlackNotifierPlugin {

    /**
     * Plugin identity for the operator UI footer. Same `meta` shape
     * every other plugin in the catalog exposes — see
     * [com.bzonfhir.subscriptionservice.spi.meta.PluginMeta].
     */
    val meta: PluginMeta = PluginMeta(
        id = "slack-notifier",
        version = "0.1.0",
        schemaVersion = 1,
        supplier = PluginSupplier.COMMUNITY,
        description = "Forward DLQ-size and error-rate events to Slack via incoming webhook",
    )

    @Bean
    @ConditionalOnMissingBean
    open fun slackWebhookClient(properties: SlackNotifierProperties): SlackWebhookClient =
        SlackWebhookClient(maxRetries = properties.maxRetries)

    @Bean
    @ConditionalOnMissingBean
    open fun dlqPoller(
        properties: SlackNotifierProperties,
        webhookClient: SlackWebhookClient,
    ): DlqPoller {
        val formatter = EventFormatter()
        val log = LoggerFactory.getLogger(DlqPoller::class.java)
        return DlqPoller(
            properties = properties,
            onAlert = { event ->
                if (properties.webhookUrl.isBlank()) {
                    log.warn(
                        "DLQ rule '{}' fired but plugins.slack-notifier.webhook-url is blank",
                        event.ruleName,
                    )
                    return@DlqPoller
                }
                val payload = formatter.format(event, channelOverride = properties.channel)
                when (val result = webhookClient.post(properties.webhookUrl, payload)) {
                    is SlackWebhookClient.PostResult.Success ->
                        log.info("Slack alert posted for rule '{}'", event.ruleName)

                    is SlackWebhookClient.PostResult.Failure ->
                        log.warn(
                            "Slack POST failed for rule '{}': {}",
                            event.ruleName,
                            result.reason,
                        )
                }
            },
            clock = Clock.systemUTC(),
            mapper = jacksonObjectMapper(),
        )
    }

    @Bean
    @ConditionalOnMissingBean
    open fun dlqPollerScheduler(dlqPoller: DlqPoller): DlqPollerScheduler =
        DlqPollerScheduler(dlqPoller)

    /**
     * Thin Spring component owning the `@Scheduled` annotation. Kept
     * separate from [DlqPoller] so the poller stays trivially unit-
     * testable without standing up a Spring context.
     *
     * Default cadence: 60 seconds between ticks. Adjust via the
     * `plugins.slack-notifier.poll-interval-ms` property if Spring's
     * scheduler is configured to honor late-bound fixed-delay values
     * (see `Scheduled#fixedDelayString`).
     */
    @Component
    open class DlqPollerScheduler(private val poller: DlqPoller) {

        @Scheduled(
            fixedDelayString = "\${plugins.slack-notifier.poll-interval-ms:60000}",
            initialDelayString = "\${plugins.slack-notifier.initial-delay-ms:30000}",
        )
        fun runOnce() {
            try {
                poller.tick()
            } catch (e: Exception) {
                // Defense-in-depth: DlqPoller already swallows
                // exceptions internally, but the scheduler must never
                // propagate to Spring or the periodic task gets killed.
                LoggerFactory.getLogger(javaClass).warn("DLQ poller tick failed: {}", e.message)
            }
        }
    }

    companion object {
        /** Default cadence between polls. Mirrored as the fixedDelay default above. */
        val DEFAULT_POLL_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
