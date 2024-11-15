package account.infra

import account.app.domain.Event
import account.infra.inbound.TriggerUseCaseAccountEventHandler
import account.infra.outbound.EventStoreAccountRepository.Companion.ACCOUNT_EVENTS_STREAM_PREFIX
import account.infra.outbound.eventhandlers.PublishMetricsHandler
import account.infra.outbound.eventhandlers.PublishToPulsarHandler
import account.infra.outbound.eventhandlers.UpdateAccountProjectionHandler
import account.infra.outbound.eventhandlers.WriteLogsHandler
import com.eventstore.dbclient.CreatePersistentSubscriptionToAllOptions
import com.eventstore.dbclient.EventStoreDBPersistentSubscriptionsClient
import com.eventstore.dbclient.NackAction.Retry
import com.eventstore.dbclient.NamedConsumerStrategy.PINNED
import com.eventstore.dbclient.PersistentSubscription
import com.eventstore.dbclient.PersistentSubscriptionListener
import com.eventstore.dbclient.ResolvedEvent
import com.eventstore.dbclient.SubscriptionFilter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.invoke.MethodHandles
import java.util.concurrent.CompletableFuture

private val domainEventsPkg = "account.app.domain"

data class RetryStrategy(val maxNumberOfRetries: Int = 500, val retryDelay: Long = 50L, val applyDelaysAfter: Int = 100)

@Component
class EventStoreSubscriptionRegistry(
    private val client: EventStoreDBPersistentSubscriptionsClient,
    private val writeLogsHandler: WriteLogsHandler,
    private val publishToPulsarHandler: PublishToPulsarHandler,
    private val updateAccountProjectionHandler: UpdateAccountProjectionHandler,
    private val publishMetricsHandler: PublishMetricsHandler,
    private val triggerUseCaseAccountEventHandler: TriggerUseCaseAccountEventHandler,
    private val meterRegistry: MeterRegistry,
    private val retryStrategy: RetryStrategy = RetryStrategy(500, 50L, 100)
) {

    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    init {
        createPersistentSubscription(streamPrefix = ACCOUNT_EVENTS_STREAM_PREFIX, groupName = "side-effects")
        subscribeToStream(consumerGroup = "side-effects", handle = ::dispatch)
    }

    private suspend fun dispatch(subscription: PersistentSubscription, retryCount: Int, event: ResolvedEvent) {
        try {
            val eventClass = Class.forName("$domainEventsPkg.${event.event.eventType}") as Class<out Event>
            val domainEvent: Event = mapper.readValue(event.event.eventData, eventClass)
            updateAccountProjectionHandler.handleEvent(domainEvent, event.event.revision)
            publishToPulsarHandler.handleEvent(domainEvent)
            publishMetricsHandler.handleEvent(domainEvent)
            writeLogsHandler.handleEvent(domainEvent)
            triggerUseCaseAccountEventHandler.handleEvent(domainEvent)
            subscription.ack(event)
        } catch (e: Exception) {
            handleRetry(event, retryCount, e, subscription)
        }
    }

    private suspend fun handleRetry(event: ResolvedEvent, retryCount: Int, e: Exception, subscription: PersistentSubscription) {
        with(retryStrategy) {
            logger.error("Failed to dispatch event ${event.event.eventId} to handlers, retry #$retryCount", e)
            if (retryCount >= maxNumberOfRetries) {
                logger.error("Maximum retry limit reached for event ${event.event.eventId}. Stopping subscription.")
                meterRegistry.counter(
                    "subscription.account-side-effects.stopped",
                    listOf(Tag.of("exception", "${e::class.java.simpleName}:${e.message}"))
                ).increment()
                subscription.stop()
                return
            }
            val delayTime =
                if (retryCount >= applyDelaysAfter) retryDelay * (retryCount - (applyDelaysAfter - 1)) else 0L
            if (delayTime > 0) {
                logger.warn("Applying delay of ${delayTime} ms before retrying...")
                delay(delayTime)
            }
            subscription.nack(Retry, e.message, event)
        }
    }

    private fun createPersistentSubscription(streamPrefix: String, groupName: String) {
        val options = CreatePersistentSubscriptionToAllOptions.get()
            .namedConsumerStrategy(PINNED) // Assign each stream consistently to the same consumer
            .maxRetryCount(Int.MAX_VALUE) // max retry, will be controlled by the consumer
            .fromEnd()
            .filter(SubscriptionFilter.newBuilder().addStreamNamePrefix(streamPrefix).build())
        val futureResult: CompletableFuture<Any> = client.createToAll(groupName, options)
            .thenAccept { logger.info("Persistent subscription group '$groupName' created for all streams like '$streamPrefix'") }
        futureResult.exceptionally { throwable ->
            logger.error("Failed to create persistent subscription group '$groupName' created for all streams like '$streamPrefix': $throwable")
            throw throwable
        }.get()
    }

    private fun subscribeToStream(consumerGroup: String, handle: suspend (PersistentSubscription, Int, ResolvedEvent) -> Unit) {
        client.subscribeToAll(consumerGroup, object : PersistentSubscriptionListener() {
            override fun onEvent(subscription: PersistentSubscription, retryCount: Int, event: ResolvedEvent) {
                runBlocking { handle(subscription, retryCount, event) }
            }
        }).thenAccept { _ -> logger.info("Consumer successfully subscribed to '$consumerGroup'.") }
            .exceptionally {
                logger.error("Consumer failed subscribing to $consumerGroup: ${it.message}")
                throw it
            }.get()
    }
}

//val inFlightMessages = mutableMapOf<String, ResolvedEvent>()
//
//fun processEvent(event: ResolvedEvent) {
//    val streamId = event.event.streamId
//
//    if (inFlightMessages.containsKey(streamId)) {
//         Wait until the current message is fully processed
//        return // it will wait
//    }
//
//    inFlightMessages[streamId] = event
//
//    try {
//         Process the event
//        acknowledgeEvent(event)
//    } finally {
//        inFlightMessages.remove(streamId)
//    }
//}