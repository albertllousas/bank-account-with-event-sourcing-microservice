package account.infra.outbound.eventhandlers

import account.acceptance.BaseAcceptanceTest
import account.acceptance.BaseAcceptanceTest.Companion
import account.fixtures.TestBuilders
import account.fixtures.containers.Pulsar
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountClosedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountClosingFailedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountCreditFailedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountCreditedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountDebitFailedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountDebitedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountInitiatedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountInitiationFailedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountOpenedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountOpeningFailedEvent
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("integration")
class PublishToPulsarHandlerTest {

    private val pulsar = Pulsar.getInstance()

    private val publishToPulsarHandler = PublishToPulsarHandler(pulsar.client, "account.events")

    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @AfterEach
    fun tearDown() {
        pulsar.cleanup()
    }

    @Test
    fun `should publish a external event to the pulsar stream to communicate with other bounded contexts`() {
        val consumer = pulsar.client.newConsumer().topic("account.events").subscriptionName("test").subscribe()

        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountInitiated())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountInitiationFailed())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountOpened())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountOpeningFailed())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountCredited())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountCreditFailed())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountDebited())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountDebitFailed())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountClosed())
        publishToPulsarHandler.handleEvent(TestBuilders.Events.buildAccountClosingFailed())

        val messages = Pulsar.drain(consumer, 8)
        messages.map { mapper.readValue<ExternalAccountEvent>(it.data)::class } shouldBe listOf(
            AccountInitiatedEvent::class,
            AccountInitiationFailedEvent::class,
            AccountOpenedEvent::class,
            AccountOpeningFailedEvent::class,
            AccountCreditedEvent::class,
            AccountCreditFailedEvent::class,
            AccountDebitedEvent::class,
            AccountDebitFailedEvent::class,
            AccountClosedEvent::class,
            AccountClosingFailedEvent::class
        )
    }
}