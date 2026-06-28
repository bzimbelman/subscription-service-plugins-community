package com.bzonfhir.community.slacknotifier

import com.bzonfhir.community.slacknotifier.config.SlackNotifierProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * Polls the subscription-service `/admin/observe/dlq` admin endpoint
 * and emits an [AlertEvent.DlqThresholdCrossed] whenever a configured
 * `dlq-threshold` rule's threshold is exceeded.
 *
 * Cooldown bookkeeping is per-rule: a rule that fired at `t` is
 * suppressed until `t + cooldownSeconds`. This prevents a stuck DLQ
 * from spamming Slack every polling tick.
 *
 * The poller never throws — admin-endpoint outages and JSON parse
 * failures are logged and the tick exits cleanly. The next tick will
 * try again.
 *
 * The poller is decoupled from Slack delivery. It pushes events to the
 * [onAlert] callback; in production that callback formats and POSTs;
 * in tests it captures.
 */
class DlqPoller(
    private val properties: SlackNotifierProperties,
    private val onAlert: (AlertEvent) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) {

    private val log = LoggerFactory.getLogger(DlqPoller::class.java)

    /** Last-fired timestamp per rule, used for cooldown evaluation. */
    private val lastFiredAt: ConcurrentHashMap<String, Instant> = ConcurrentHashMap()

    /**
     * One polling pass. Spring's `@Scheduled` calls this on a fixed
     * cadence (see [com.bzonfhir.community.slacknotifier.SlackNotifierPlugin]);
     * tests call it manually.
     */
    fun tick() {
        if (!properties.enabled) return
        val dlqRules = properties.rules.filter { it.type == "dlq-threshold" }
        if (dlqRules.isEmpty()) return

        val (total, samples) = fetchDlq() ?: return
        val now = clock.instant()

        for (rule in dlqRules) {
            val threshold = rule.threshold ?: continue
            if (total <= threshold) continue
            if (!cooldownElapsed(rule, now)) {
                log.debug("Rule '{}' cooldown active; suppressing alert", rule.name)
                continue
            }

            lastFiredAt[rule.name] = now
            onAlert(
                AlertEvent.DlqThresholdCrossed(
                    ruleName = rule.name,
                    threshold = threshold,
                    observedSize = total,
                    observedAt = now,
                    sampleErrors = samples,
                ),
            )
        }
    }

    private fun cooldownElapsed(rule: SlackNotifierProperties.Rule, now: Instant): Boolean {
        val last = lastFiredAt[rule.name] ?: return true
        return Duration.between(last, now).seconds >= rule.cooldownSeconds
    }

    /**
     * Returns `(totalDlqSize, sampleErrors)` from the admin endpoint,
     * or `null` on any failure.
     */
    private fun fetchDlq(): Pair<Int, List<String>>? {
        return try {
            val uri = URI.create("${properties.adminBaseUrl.trimEnd('/')}/admin/observe/dlq?limit=20")
            val builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
            properties.adminBearerToken?.let {
                builder.header("Authorization", "Bearer $it")
            }
            val response = httpClient.send(builder.build(), BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                log.warn("DLQ admin endpoint returned {}", response.statusCode())
                return null
            }
            val node: JsonNode = mapper.readTree(response.body())
            val total = node.path("total").asInt(-1)
            if (total < 0) {
                log.warn("DLQ admin response missing or invalid 'total' field")
                return null
            }
            val items = node.path("items")
            val samples = if (items.isArray) {
                items.asSequence()
                    .mapNotNull {
                        val err = it.path("last_error")
                        if (err.isMissingNode || err.isNull) null else err.asText()
                    }
                    .take(MAX_SAMPLE_ERRORS)
                    .toList()
            } else {
                emptyList()
            }
            Pair(total, samples)
        } catch (e: Exception) {
            log.warn("DLQ admin endpoint call failed: {}", e.message)
            null
        }
    }

    private companion object {
        /** Cap on sample-error lines included in a Slack alert. */
        private const val MAX_SAMPLE_ERRORS = 5
    }
}
