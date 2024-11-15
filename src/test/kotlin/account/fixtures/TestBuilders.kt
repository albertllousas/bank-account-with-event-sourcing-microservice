package account.fixtures

import account.app.domain.Account
import account.app.domain.AccountCreditFailed
import account.app.domain.AccountInitiated
import account.app.domain.AccountInitiationFailed
import account.app.domain.AccountStatus
import account.app.domain.AccountType
import account.app.domain.DomainEvent
import account.app.domain.TransactionSource
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.Currency
import java.util.UUID
import java.util.UUID.randomUUID

object TestBuilders {

    val EURO = Currency.getInstance("EUR")

    object Account {
        fun build(
            id: UUID = randomUUID(),
            revision: Long = 0,
            customerId: UUID = randomUUID(),
            currency: Currency = EURO,
            type: AccountType = AccountType.MAIN,
            status: AccountStatus = AccountStatus.Initiated,
            balance: BigDecimal = BigDecimal.ZERO,
            events: List<DomainEvent> = emptyList(),
            clock: Clock = Clock.systemUTC(),
            eventId: (() -> UUID)? = null
        ): account.app.domain.Account =
            Account(
                id,
                customerId,
                currency,
                type,
                revision,
                status,
                balance,
                events,
                eventId ?: account.app.domain.Account.generateId,
                clock
            )
    }

    object Events {

        fun buildAccountInitiated(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            customerId: UUID = randomUUID(),
            currency: String = "EUR",
            type: String = "MAIN",
            on: LocalDateTime = now(),
        ): AccountInitiated = AccountInitiated(eventId, accountId, on, customerId, currency, type)

        fun buildAccountInitiationFailed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            reason: AccountInitiationFailed.Reason = AccountInitiationFailed.Reason.INVALID_ACCOUNT_TYPE,
            on: LocalDateTime = now(),
        ): AccountInitiationFailed = AccountInitiationFailed(eventId, accountId, on, reason)

        fun buildAccountOpened(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            on: LocalDateTime = now(),
        ): account.app.domain.AccountOpened = account.app.domain.AccountOpened(eventId, accountId, on)

        fun buildAccountCredited(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            amount: BigDecimal = BigDecimal.TEN,
            transactionId: UUID = randomUUID(),
            source: TransactionSource = TransactionSource.SEPA_TRANSFER,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountCredited = account.app.domain.AccountCredited(
            eventId, accountId, on, amount, transactionId, source
        )

        fun buildAccountCreditFailed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            reason: AccountCreditFailed.Reason = AccountCreditFailed.Reason.NON_POSITIVE_AMOUNT,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountCreditFailed = account.app.domain.AccountCreditFailed(eventId, accountId, on, reason)

        fun buildAccountOpeningFailed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            reason: account.app.domain.AccountOpeningFailed.Reason = account.app.domain.AccountOpeningFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountOpeningFailed = account.app.domain.AccountOpeningFailed(eventId, accountId, on, reason)

        fun buildAccountDebited(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            amount: BigDecimal = BigDecimal.TEN,
            transactionId: UUID = randomUUID(),
            source: TransactionSource = TransactionSource.SEPA_TRANSFER,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountDebited = account.app.domain.AccountDebited(
            eventId, accountId, on, amount, transactionId, source
        )

        fun buildAccountDebitFailed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            reason: account.app.domain.AccountDebitFailed.Reason = account.app.domain.AccountDebitFailed.Reason.NON_POSITIVE_AMOUNT,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountDebitFailed = account.app.domain.AccountDebitFailed(eventId, accountId, on, reason)

        fun buildAccountClosed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            on: LocalDateTime = now(),
        ): account.app.domain.AccountClosed = account.app.domain.AccountClosed(eventId, accountId, on)

        fun buildAccountClosingFailed(
            eventId: UUID = randomUUID(),
            accountId: UUID = randomUUID(),
            reason: account.app.domain.AccountClosingFailed.Reason = account.app.domain.AccountClosingFailed.Reason.FUNDS_STILL_PRESENT,
            on: LocalDateTime = now(),
        ): account.app.domain.AccountClosingFailed = account.app.domain.AccountClosingFailed(eventId, accountId, on, reason)
    }
}
