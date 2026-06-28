package com.bzonfhir.community.slacknotifier

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The client wraps an HTTP POST to a Slack incoming-webhook. We exercise
 * three concrete behaviors:
 *
 *   1. On 200, the client reports success after one attempt.
 *   2. On 5xx, the client retries up to `maxRetries` additional times.
 *   3. On exhaust, the client reports failure (and never throws — the
 *      poller treats failure as "log + cooldown", not "crash").
 *
 * We use WireMock as a real local HTTP server so the test exercises the
 * full network stack (URL parsing, body serialization, retry loop). No
 * real Slack endpoint is contacted.
 */
class SlackWebhookClientTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var webhookUrl: String

    @BeforeEach
    fun start() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        // Path emulates Slack's webhook URL shape (`/services/T.../B.../...`)
        // without using the real format. `placeholder1` etc. stand in for
        // the workspace/channel/secret tokens.
        webhookUrl = "${wireMock.baseUrl()}/services/placeholder1/placeholder2/placeholder3"
    }

    @AfterEach
    fun stop() {
        wireMock.stop()
    }

    @Test
    fun `200 response is reported as success and posts the payload`() {
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .willReturn(aResponse().withStatus(200).withBody("ok")),
        )

        val client = SlackWebhookClient(maxRetries = 3, retryBackoff = Duration.ofMillis(1))
        val result = client.post(webhookUrl, mapOf("text" to "hello"))

        assertThat(result).isInstanceOf(SlackWebhookClient.PostResult.Success::class.java)
        wireMock.verify(
            postRequestedFor(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .withRequestBody(equalToJson("""{"text":"hello"}""")),
        )
    }

    @Test
    fun `5xx is retried up to maxRetries then succeeds when the upstream recovers`() {
        val scenarioName = "retry-on-5xx"
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .inScenario(scenarioName)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt-2"),
        )
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("attempt-2")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("attempt-3"),
        )
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .inScenario(scenarioName)
                .whenScenarioStateIs("attempt-3")
                .willReturn(aResponse().withStatus(200).withBody("ok")),
        )

        val client = SlackWebhookClient(maxRetries = 3, retryBackoff = Duration.ofMillis(1))
        val result = client.post(webhookUrl, mapOf("text" to "hello"))

        assertThat(result).isInstanceOf(SlackWebhookClient.PostResult.Success::class.java)
        // 1 original + 2 retries = 3 attempts.
        wireMock.verify(
            3,
            postRequestedFor(urlEqualTo("/services/placeholder1/placeholder2/placeholder3")),
        )
    }

    @Test
    fun `5xx that never recovers reports failure after maxRetries`() {
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .willReturn(aResponse().withStatus(503)),
        )

        val client = SlackWebhookClient(maxRetries = 2, retryBackoff = Duration.ofMillis(1))
        val result = client.post(webhookUrl, mapOf("text" to "hello"))

        assertThat(result).isInstanceOf(SlackWebhookClient.PostResult.Failure::class.java)
        // 1 original + 2 retries = 3 attempts.
        wireMock.verify(
            3,
            postRequestedFor(urlEqualTo("/services/placeholder1/placeholder2/placeholder3")),
        )
    }

    @Test
    fun `4xx is reported as failure WITHOUT retry`() {
        wireMock.stubFor(
            post(urlEqualTo("/services/placeholder1/placeholder2/placeholder3"))
                .willReturn(aResponse().withStatus(400)),
        )

        val client = SlackWebhookClient(maxRetries = 5, retryBackoff = Duration.ofMillis(1))
        val result = client.post(webhookUrl, mapOf("text" to "hello"))

        assertThat(result).isInstanceOf(SlackWebhookClient.PostResult.Failure::class.java)
        // Exactly one attempt — 4xx is not retried.
        wireMock.verify(
            1,
            postRequestedFor(urlEqualTo("/services/placeholder1/placeholder2/placeholder3")),
        )
    }
}
