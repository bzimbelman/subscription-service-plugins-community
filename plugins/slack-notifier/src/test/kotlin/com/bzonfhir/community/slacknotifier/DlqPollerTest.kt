package com.bzonfhir.community.slacknotifier

import com.bzonfhir.community.slacknotifier.config.SlackNotifierProperties
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * The poller polls `/admin/observe/dlq`, applies every configured
 * `dlq-threshold` rule, and emits one [AlertEvent.DlqThresholdCrossed]
 * per rule-firing — exactly once until the rule's cooldown elapses.
 *
 * We test:
 *
 *   1. Below threshold → no event.
 *   2. Above threshold → exactly one event, with the right rule name +
 *      observed size + sample errors.
 *   3. Cooldown respected: a second tick within cooldown emits nothing,
 *      even though the DLQ is still over threshold.
 *   4. Cooldown lapses → the rule fires again.
 *
 * The poller is decoupled from the actual `SlackWebhookClient` — it just
 * pushes events to a callback. In production the callback formats and
 * posts; in tests it appends to a list.
 */
class DlqPollerTest {

    private lateinit var wireMock: WireMockServer
    private lateinit var adminBaseUrl: String

    @BeforeEach
    fun start() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
        adminBaseUrl = wireMock.baseUrl()
    }

    @AfterEach
    fun stop() {
        wireMock.stop()
    }

    private fun stubDlqResponse(total: Int, errors: List<String> = emptyList()) {
        val items = errors.mapIndexed { i, err ->
            """{"id": ${i + 1}, "last_error": "$err", "status": "DEAD_LETTER"}"""
        }.joinToString(",")
        wireMock.stubFor(
            get(urlPathEqualTo("/admin/observe/dlq")).willReturn(
                aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                    """{"schema_version":"1","total":$total,"limit":20,"items":[$items]}""",
                ),
            ),
        )
    }

    private fun makeProperties(rules: List<SlackNotifierProperties.Rule>) = SlackNotifierProperties(
        enabled = true,
        webhookUrl = "http://placeholder.invalid/services/x/y/z",
        channel = null,
        adminBaseUrl = adminBaseUrl,
        adminBearerToken = null,
        maxRetries = 3,
        rules = rules,
    )

    @Test
    fun `below threshold emits no event`() {
        stubDlqResponse(total = 5)
        val captured = mutableListOf<AlertEvent>()
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "dlq-size",
                        type = "dlq-threshold",
                        threshold = 10,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
        )

        poller.tick()

        assertThat(captured).isEmpty()
    }

    @Test
    fun `above threshold emits one event with rule name and sample errors`() {
        stubDlqResponse(
            total = 42,
            errors = listOf("HL7 parse error at MSH-9", "Connection refused: lims.example.test:8080"),
        )
        val captured = mutableListOf<AlertEvent>()
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "dlq-size",
                        type = "dlq-threshold",
                        threshold = 10,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
        )

        poller.tick()

        assertThat(captured).hasSize(1)
        val event = captured.first() as AlertEvent.DlqThresholdCrossed
        assertThat(event.ruleName).isEqualTo("dlq-size")
        assertThat(event.threshold).isEqualTo(10)
        assertThat(event.observedSize).isEqualTo(42)
        assertThat(event.sampleErrors).contains("HL7 parse error at MSH-9")
    }

    @Test
    fun `cooldown suppresses a second tick within the window`() {
        stubDlqResponse(total = 42)
        val captured = mutableListOf<AlertEvent>()
        val mutableClock = MutableClock(Instant.parse("2026-06-28T00:00:00Z"))
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "dlq-size",
                        type = "dlq-threshold",
                        threshold = 10,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = mutableClock,
        )

        poller.tick()
        // 5 minutes later — still inside the 10-minute cooldown.
        mutableClock.advance(Duration.ofMinutes(5))
        poller.tick()

        assertThat(captured).hasSize(1)
    }

    @Test
    fun `firing again after the cooldown lapses`() {
        stubDlqResponse(total = 42)
        val captured = mutableListOf<AlertEvent>()
        val mutableClock = MutableClock(Instant.parse("2026-06-28T00:00:00Z"))
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "dlq-size",
                        type = "dlq-threshold",
                        threshold = 10,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = mutableClock,
        )

        poller.tick()
        mutableClock.advance(Duration.ofMinutes(11)) // past cooldown
        poller.tick()

        assertThat(captured).hasSize(2)
    }

    @Test
    fun `non-dlq-threshold rule types are ignored`() {
        stubDlqResponse(total = 999)
        val captured = mutableListOf<AlertEvent>()
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "error-rate",
                        type = "error-rate",
                        thresholdPercent = 5.0,
                        windowMinutes = 5,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
        )

        poller.tick()

        // error-rate rules aren't evaluated by the DLQ poller —
        // they're reserved for a follow-up iteration.
        assertThat(captured).isEmpty()
    }

    @Test
    fun `admin endpoint failure does not throw`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/admin/observe/dlq")).willReturn(aResponse().withStatus(500)),
        )
        val captured = mutableListOf<AlertEvent>()
        val poller = DlqPoller(
            properties = makeProperties(
                listOf(
                    SlackNotifierProperties.Rule(
                        name = "dlq-size",
                        type = "dlq-threshold",
                        threshold = 10,
                        cooldownSeconds = 600,
                    ),
                ),
            ),
            onAlert = { captured.add(it) },
            clock = Clock.fixed(Instant.parse("2026-06-28T00:00:00Z"), ZoneOffset.UTC),
        )

        // Should not throw — the poller is best-effort.
        poller.tick()
        assertThat(captured).isEmpty()
    }

    /** Manual clock for cooldown tests. */
    private class MutableClock(start: Instant) : Clock() {
        @Volatile private var now: Instant = start
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun getZone(): java.time.ZoneId = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
}
