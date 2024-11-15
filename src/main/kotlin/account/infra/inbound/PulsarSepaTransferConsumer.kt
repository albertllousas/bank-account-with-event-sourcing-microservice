package account.infra.inbound

import account.app.domain.TransactionSource.SEPA_TRANSFER
import account.app.usecases.CreditAccountUseCase
import account.app.usecases.DebitAccountUseCase
import account.infra.inbound.Direction.INCOMING
import account.infra.inbound.Direction.OUTGOING
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
import java.util.concurrent.TimeUnit.*

@Component
class PulsarSepaTransferConsumer(
    client: PulsarClient,
    private val creditAccountFunds: CreditAccountUseCase,
    private val debitAccountFunds: DebitAccountUseCase,
    topic: String = "sepa-transfers.events",
    subscriptionName: String = "accounts-service.sepa-transfers.subscription",
    dlqTopic: String = "accounts-service.sepa-transfers.events.dlq",
    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()),
) {

    private val deadLetterPolicy = DeadLetterPolicy.builder()
        .maxRedeliverCount(10)
        .deadLetterTopic(dlqTopic)
        .build()

    private val consumer: Consumer<SepaTransferEvent> =
        client
            .newConsumer(Schema.JSON(SepaTransferEvent::class.java))
            .topic(topic)
            .negativeAckRedeliveryDelay(50, MILLISECONDS)
            .subscriptionName(subscriptionName)
            .subscriptionType(Key_Shared)
            .deadLetterPolicy(deadLetterPolicy)
            .messageListener { _, msg -> handleIncomingMessage(msg) }
            .subscribe()

    private fun handleIncomingMessage(message: Message<SepaTransferEvent>) {
        try {
            with(message.value) {
                when {
                    this is SepaTransferEvent.Accepted && direction == INCOMING ->
                        creditAccountFunds(transactionId, accountId, amount, currency, SEPA_TRANSFER)
                    this is SepaTransferEvent.Accepted && direction == OUTGOING ->
                        debitAccountFunds(transactionId, accountId, amount, currency, SEPA_TRANSFER)
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
External SEPA event
*/
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SepaTransferEvent.Initiated::class, name = "initiated"),
    JsonSubTypes.Type(value = SepaTransferEvent.Accepted::class, name = "accepted")
)
sealed class SepaTransferEvent {
    abstract val transactionId: UUID
    abstract val accountId: UUID
    abstract val eventType: String

    data class Initiated(
        override val transactionId: UUID,
        override val eventType: String = "initiated",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String,
    ) : SepaTransferEvent()

    data class Accepted(
        override val transactionId: UUID,
        override val eventType: String = "accepted",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val direction: Direction
    ) : SepaTransferEvent()
}

enum class Direction {
    INCOMING, OUTGOING
}
// Skipped other possible event like Processing, Settled, Credited, Completed, Returned ... Just assuming, I am not an expert on this domain
// ignoring also other properties like payment details since we are not interested in
