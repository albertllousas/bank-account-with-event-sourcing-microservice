package account.infra.outbound.eventhandlers

import account.app.domain.AccountInitiationFailed.Reason.INVALID_ACCOUNT_TYPE
import account.fixtures.TestBuilders
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class PublishMetricsHandlerTest {

    private val metrics = SimpleMeterRegistry()

    private val metricsEventPublisher = PublishMetricsHandler(metrics)

    @Test
    fun `should publish a metric for a domain event`() {
        val event = TestBuilders.Events.buildAccountInitiated()

        metricsEventPublisher.handleEvent(event)

        metrics.counter("domain.event", listOf(Tag.of("type", "AccountInitiated"))).count() shouldBe 1.0
    }

    @Test
    fun `should publish a metric for a domain error`() {
        val event = TestBuilders.Events.buildAccountInitiationFailed(reason = INVALID_ACCOUNT_TYPE)

        metricsEventPublisher.handleEvent(event)

        metrics.counter(
            "domain.error", listOf(Tag.of("type", "AccountInitiationFailed"), Tag.of("error", "INVALID_ACCOUNT_TYPE"))
        ).count() shouldBe 1.0
    }
}
