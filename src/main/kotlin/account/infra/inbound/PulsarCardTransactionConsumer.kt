package account.infra.inbound

import account.app.domain.TransactionSource.CARD_TX
import account.app.usecases.DebitAccountUseCase
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.pulsar.client.api.Consumer
import org.apache.pulsar.client.api.DeadLetterPolicy
import org.apache.pulsar.client.api.Message
import org.apache.pulsar.client.api.PulsarClient
import org.apache.pulsar.client.api.Schema
import org.apache.pulsar.client.api.SubscriptionType.Key_Shared
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS

@Component
class PulsarCardTransactionConsumer(
    client: PulsarClient,
    private val debitAccountFunds: DebitAccountUseCase,
    topic: String = "card-transaction.events",
    subscriptionName: String = "accounts-service.card-transaction.subscription",
    dlqTopic: String = "accounts-service.card-transaction.events.dlq",
    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()),
) {

    private val deadLetterPolicy = DeadLetterPolicy.builder()
        .maxRedeliverCount(10)
        .deadLetterTopic(dlqTopic)
        .build()

    private val consumer: Consumer<CardTransactionEvent> = client
        .newConsumer(Schema.JSON(CardTransactionEvent::class.java))
        .topic(topic)
        .negativeAckRedeliveryDelay(50, MILLISECONDS)
        .subscriptionName(subscriptionName)
        .subscriptionType(Key_Shared)
        .deadLetterPolicy(deadLetterPolicy)
        .messageListener { _, msg -> handleIncomingMessage(msg) }
        .subscribe()


    private fun handleIncomingMessage(message: Message<CardTransactionEvent>) {
        try {
            with(message.value) {
                when  {
                    this is CardTransactionEvent.Authorized ->
                        debitAccountFunds(transactionId, accountId, amount, currency, CARD_TX)
                    else -> {}
                }
                consumer.acknowledge(message)
            }
        } catch (e: Exception) {
            logger.error("Processing failed for message '${message.messageId}, retrying #${message.redeliveryCount}'", e)
            consumer.negativeAcknowledge(message) // Trigger retry and eventually DLQ if retries are exhausted
        }
    }

    fun close() = consumer.close()
}

/*
External card event
*/
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CardTransactionEvent.Initiated::class, name = "initiated"),
    JsonSubTypes.Type(value = CardTransactionEvent.Authorized::class, name = "authorized")
)
sealed class CardTransactionEvent {
    abstract val transactionId: UUID
    abstract val accountId: UUID
    abstract val eventType: String


    data class Initiated(
        override val transactionId: UUID,
        override val eventType: String = "initiated",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String
    ) : CardTransactionEvent()

    data class Authorized(
        override val transactionId: UUID,
        override val eventType: String = "accepted",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String
    ) : CardTransactionEvent()
}

// Skipped other possible event like completed, disputed ... Just assuming, I am not an expert on this domain
// ignoring also other properties like transaction card details since we are not interested in
