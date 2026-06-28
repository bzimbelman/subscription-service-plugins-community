package com.bzonfhir.community.slacknotifier

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Posts a Slack-shaped JSON body to a configured incoming-webhook URL.
 *
 * Built on the JDK [HttpClient] — no third-party HTTP library, no
 * Spring `RestTemplate`. We want this plugin's runtime footprint tiny.
 *
 * Retry semantics:
 *   - HTTP 2xx → success, one attempt.
 *   - HTTP 5xx → transient; retry up to [maxRetries] more times with
 *     [retryBackoff] between attempts.
 *   - HTTP 4xx → permanent; report failure WITHOUT retry. The webhook
 *     URL is probably wrong; retrying will not help and Slack's API
 *     rate-limits aggressively on repeat 4xx.
 *   - IOException → transient; retry up to [maxRetries] more times.
 *
 * The client never throws on a delivery failure — Slack delivery is
 * best-effort. The poller treats a [PostResult.Failure] as "log and
 * cooldown," not as a fatal condition.
 */
class SlackWebhookClient(
    private val maxRetries: Int = 3,
    private val retryBackoff: Duration = Duration.ofSeconds(2),
    private val requestTimeout: Duration = Duration.ofSeconds(10),
    private val mapper: ObjectMapper = jacksonObjectMapper(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
) {

    /**
     * Result of a single [post] invocation. Sealed so callers can
     * `when`-exhaustively dispatch on success vs failure.
     */
    sealed class PostResult {
        /** Slack returned a 2xx response. */
        object Success : PostResult()

        /**
         * Slack returned a non-2xx response or the network call failed
         * even after retries. [reason] is human-readable; surfaces in
         * the plugin's logs.
         */
        data class Failure(val reason: String) : PostResult()
    }

    /**
     * POST [payload] (serialized to JSON via Jackson) to [webhookUrl].
     *
     * @return a [PostResult].
     */
    fun post(webhookUrl: String, payload: Map<String, Any>): PostResult {
        val body = mapper.writeValueAsString(payload)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(webhookUrl))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        var attempt = 0
        var lastReason = "unknown"

        while (attempt <= maxRetries) {
            try {
                val response = httpClient.send(request, BodyHandlers.ofString())
                val status = response.statusCode()
                if (status in 200..299) {
                    return PostResult.Success
                }
                if (status in 400..499) {
                    // Permanent — bad URL, malformed body, channel
                    // doesn't exist. Don't waste retries.
                    return PostResult.Failure("Slack returned $status: ${response.body()}")
                }
                // 5xx → fall through to retry.
                lastReason = "Slack returned $status: ${response.body()}"
            } catch (e: Exception) {
                // Network errors are transient. InterruptedException
                // also lands here — we re-set the interrupt flag and
                // bail.
                if (e is InterruptedException) {
                    Thread.currentThread().interrupt()
                    return PostResult.Failure("interrupted")
                }
                lastReason = "${e::class.simpleName}: ${e.message}"
            }

            attempt += 1
            if (attempt <= maxRetries) {
                try {
                    Thread.sleep(retryBackoff.toMillis())
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return PostResult.Failure("interrupted")
                }
            }
        }

        return PostResult.Failure("after ${maxRetries + 1} attempts: $lastReason")
    }
}
