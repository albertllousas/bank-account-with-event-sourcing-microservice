package account.infra.outbound.eventhandlers

import account.app.domain.AccountClosed
import account.app.domain.AccountClosingFailed
import account.app.domain.AccountCreditFailed
import account.app.domain.AccountCredited
import account.app.domain.AccountDebitFailed
import account.app.domain.AccountDebited
import account.app.domain.AccountOpened
import account.app.domain.AccountInitiated
import account.app.domain.AccountInitiationFailed
import account.app.domain.AccountOpeningFailed
import account.app.domain.Event
import account.infra.outbound.eventhandlers.ExternalAccountEvent.*
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.pulsar.client.api.PulsarClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class PublishToPulsarHandler(
    pulsarClient: PulsarClient,
    accountsTopic: String = "account.events",
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) {

    private val producer = pulsarClient.newProducer().topic(accountsTopic).create()

    fun handleEvent(event: Event) {
        producer.newMessage()
            .key(event.accountId.toString())
            .value(mapper.writeValueAsBytes(event.toExternalEvent()))
            .send()
    }
}

private fun Event.toExternalEvent(): ExternalAccountEvent =
    when (this) {
        is AccountInitiated -> AccountInitiatedEvent(eventId, accountId, on, customerId, currency, type)
        is AccountInitiationFailed -> AccountInitiationFailedEvent(eventId, accountId, on, reason)
        is AccountOpened -> AccountOpenedEvent(eventId, accountId, on)
        is AccountOpeningFailed -> AccountOpeningFailedEvent(eventId, accountId, on, reason)
        is AccountCreditFailed -> AccountCreditFailedEvent(eventId, accountId, on, reason)
        is AccountCredited -> AccountCreditedEvent(eventId, accountId, on, amount.toString(), transactionId, transactionSource.name)
        is AccountDebitFailed -> AccountDebitFailedEvent(eventId, accountId, on, reason)
        is AccountDebited -> AccountDebitedEvent(eventId, accountId, on, amount.toString(), transactionId, transactionSource.name)
        is AccountClosingFailed -> AccountClosingFailedEvent(eventId, accountId, on, reason)
        is AccountClosed -> AccountClosedEvent(eventId, accountId, on)
    }

/*
External event: Event to share changes to other bounded contexts.
*/
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = AccountInitiatedEvent::class, name = "account_initiated"),
    JsonSubTypes.Type(value = AccountInitiationFailedEvent::class, name = "account_initiation_failed")
)
sealed class ExternalAccountEvent(val eventType: String) {

    abstract val eventId: UUID
    abstract val accountId: UUID
    abstract val on: LocalDateTime

    data class AccountInitiatedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val customerId: UUID,
        val currency: String,
        val type: String
    ) : ExternalAccountEvent("account_initiated")

    data class AccountInitiationFailedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val reason: AccountInitiationFailed.Reason
    ) : ExternalAccountEvent("account_initiation_failed")

    data class AccountOpenedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
    ) : ExternalAccountEvent("account_opened")

    data class AccountOpeningFailedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val reason: AccountOpeningFailed.Reason
    ) : ExternalAccountEvent("account_opening_failed")

    data class AccountCreditedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val amount: String,
        val transactionId: UUID,
        val source: String
    ) : ExternalAccountEvent("account_credited")

    data class AccountCreditFailedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val reason: AccountCreditFailed.Reason
    ) : ExternalAccountEvent("account_credit_failed")

    data class AccountDebitedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val amount: String,
        val transactionId: UUID,
        val source: String
    ) : ExternalAccountEvent("account_debited")

    data class AccountDebitFailedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val reason: AccountDebitFailed.Reason
    ) : ExternalAccountEvent("account_debit_failed")

    data class AccountClosedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
    ) : ExternalAccountEvent("account_closed")

    data class AccountClosingFailedEvent(
        override val eventId: UUID,
        override val accountId: UUID,
        override val on: LocalDateTime,
        val reason: AccountClosingFailed.Reason
    ) : ExternalAccountEvent("account_closing_failed")
}
