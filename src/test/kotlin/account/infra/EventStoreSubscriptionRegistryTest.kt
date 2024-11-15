package account.infra

//import account.app.domain.AccountCreationInitiated
import account.fixtures.TestBuilders
import account.fixtures.containers.EventStoreDB
import account.infra.inbound.TriggerUseCaseAccountEventHandler
import account.infra.outbound.eventhandlers.PublishMetricsHandler
import account.infra.outbound.eventhandlers.PublishToPulsarHandler
import account.infra.outbound.eventhandlers.UpdateAccountProjectionHandler
import account.infra.outbound.eventhandlers.WriteLogsHandler
import com.eventstore.dbclient.EventData
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@Tag("integration")
class EventStoreSubscriptionRegistryTest {

    private val eventStoreDB = EventStoreDB()

    private val loggingHandler = mockk<WriteLogsHandler>(relaxed = true)
    private val metricsHandler = mockk<PublishMetricsHandler>(relaxed = true)
    private val pulsarHandler = mockk<PublishToPulsarHandler>(relaxed = true)
    private val projectionHandler = mockk<UpdateAccountProjectionHandler>(relaxed = true)
    private val triggerUseCaseAccountEventHandler = mockk<TriggerUseCaseAccountEventHandler>(relaxed = true)
    private val meterRegistry = SimpleMeterRegistry()
    private val jacksonObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    init {
        EventStoreSubscriptionRegistry(
            eventStoreDB.persistentSubscriptionsClient,
            loggingHandler,
            pulsarHandler,
            projectionHandler,
            metricsHandler,
            triggerUseCaseAccountEventHandler,
            meterRegistry,
            RetryStrategy(5, 50L, 100)
        )
    }

    @Test
    fun `should dispatch events to the handlers`() {
        val domainEvent = TestBuilders.Events.buildAccountInitiated()
        val eventId = UUID.randomUUID()
        val event =
            EventData.builderAsJson(domainEvent::class.simpleName, jacksonObjectMapper.writeValueAsBytes(domainEvent))
                .eventId(eventId)
                .build()

        eventStoreDB.client.appendToStream("account-1", event).get()

        verify(timeout = 1000) {
            loggingHandler.handleEvent(domainEvent)
            pulsarHandler.handleEvent(domainEvent)
            projectionHandler.handleEvent(domainEvent, 0)
            metricsHandler.handleEvent(domainEvent)
        }
    }

    @Test
    fun `should stop the subscription after reaching the max number of retries`() {
        val event = EventData.builderAsJson("invalid", jacksonObjectMapper().writeValueAsBytes("invalid"))
            .eventId(UUID.randomUUID())
            .build()

        eventStoreDB.client.appendToStream("account-1", event).get()

        Thread.sleep(5000)

        val info = eventStoreDB.persistentSubscriptionsClient.getInfoToAll("side-effects").get().get()
        info.stats.parkedMessageCount shouldBe 0
        info.connections.size shouldBe 0
        meterRegistry.find("subscription.account-side-effects.stopped").counter()!!.count() shouldBe 1.0
    }
}
