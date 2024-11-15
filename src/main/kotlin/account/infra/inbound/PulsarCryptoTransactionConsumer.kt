package account.infra.inbound

import account.app.domain.TransactionSource.*
import account.app.usecases.CreditAccountUseCase
import account.app.usecases.DebitAccountUseCase
import account.infra.inbound.WalletOperation.RECEIVING
import account.infra.inbound.WalletOperation.SENDING
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
class PulsarCryptoTransactionConsumer(
    client: PulsarClient,
    private val creditAccountFunds: CreditAccountUseCase,
    private val debitAccountFunds: DebitAccountUseCase,
    topic: String = "crypto-transaction.events",
    subscriptionName: String = "accounts-service.crypto-transaction.subscription",
    dlqTopic: String = "accounts-service.crypto-transaction.events.dlq",
    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()),
) {

    private val deadLetterPolicy = DeadLetterPolicy.builder()
        .maxRedeliverCount(10)
        .deadLetterTopic(dlqTopic)
        .build()

    private val consumer: Consumer<CryptoTransactionEvent> = client
        .newConsumer(Schema.JSON(CryptoTransactionEvent::class.java))
        .topic(topic)
        .negativeAckRedeliveryDelay(50, MILLISECONDS)
        .subscriptionName(subscriptionName)
        .subscriptionType(Key_Shared)
        .deadLetterPolicy(deadLetterPolicy)
        .messageListener { _, msg -> handleIncomingMessage(msg) }
        .subscribe()


    private fun handleIncomingMessage(message: Message<CryptoTransactionEvent>) {
        try {
            with(message.value) {
                when {
                    this is CryptoTransactionEvent.Confirmed && walletOperation == RECEIVING ->
                        creditAccountFunds(transactionId, accountId, amount, currency, CRYPTO_TX)
                    this is CryptoTransactionEvent.Initiated && walletOperation == SENDING ->
                        debitAccountFunds(transactionId, accountId, amount, currency, CRYPTO_TX)
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
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(value = CryptoTransactionEvent.Initiated::class, name = "initiated"),
    JsonSubTypes.Type(value = CryptoTransactionEvent.Signed::class, name = "signed"),
    JsonSubTypes.Type(value = CryptoTransactionEvent.Confirmed::class, name = "confirmed")
)
sealed class CryptoTransactionEvent {
    abstract val transactionId: UUID
    abstract val accountId: UUID
    abstract val eventType: String


    data class Initiated(
        override val transactionId: UUID,
        override val eventType: String = "initiated",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val walletOperation: WalletOperation
    ) : CryptoTransactionEvent()

    data class Signed(
        override val transactionId: UUID,
        override val eventType: String = "signed",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val walletOperation: WalletOperation
    ) : CryptoTransactionEvent()

    data class Confirmed(
        override val transactionId: UUID,
        override val eventType: String = "confirmed",
        override val accountId: UUID,
        val amount: BigDecimal,
        val currency: String,
        val walletOperation: WalletOperation
    ) : CryptoTransactionEvent()
}

enum class WalletOperation {
    RECEIVING, SENDING
}

// Skipped other possible event like broadcasted, validated, recorded or whatever ... Just assuming, I am not an expert on this domain
// ignoring also other properties like transaction crypto details since we are not interested in
