package account.app.domain

import account.app.domain.AccountCreditFailed.Reason
import account.app.domain.AccountInitiationFailed.Reason.INVALID_ACCOUNT_TYPE
import account.app.domain.AccountInitiationFailed.Reason.INVALID_CURRENCY
import account.app.domain.AccountOpeningFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS
import account.app.domain.AccountOpeningFailed.Reason.ALREADY_OPENED
import account.app.domain.AccountStatus.Closed
import account.app.domain.AccountStatus.Initiated
import account.app.domain.AccountStatus.Opened
import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import java.time.Clock
import java.time.LocalDateTime.now
import java.util.Currency
import java.util.UUID

data class Account(
    val id: UUID,
    val customerId: UUID,
    val currency: Currency,
    val type: AccountType,
    val revision: Long,
    val status: AccountStatus,
    val balance: BigDecimal,
    val events: List<DomainEvent> = emptyList(),
    val generateId: () -> UUID = Account.generateId,
    val clock: Clock = Clock.systemUTC(),
) {
    fun open(): Either<AccountOpeningFailed, Account> =
        when (status) {
            Opened -> AccountOpeningFailed(generateId(), id, now(clock), ALREADY_OPENED).left()
            Closed -> AccountOpeningFailed(generateId(), id, now(clock), ACCOUNT_NOT_ON_VALID_STATUS).left()
            else -> copy(
                status = Opened,
                revision = revision,
                events = events + AccountOpened(generateId(), id, now(clock))
            ).right()
        }

    fun credit(
        transactionId: UUID, amount: BigDecimal, currency: String, source: TransactionSource
    ): Either<AccountCreditFailed, Account> = either {
        ensure(currency == this@Account.currency.currencyCode) {
            AccountCreditFailed(transactionId, id, now(clock), Reason.INVALID_CURRENCY)
        }
        ensure(status == Opened) {
            AccountCreditFailed(transactionId, id, now(clock), Reason.ACCOUNT_NOT_ON_VALID_STATUS)
        }
        ensure(amount > ZERO) {
            AccountCreditFailed(transactionId, id, now(clock), Reason.NON_POSITIVE_AMOUNT)
        }
        copy(
            balance = balance + amount,
            revision = revision,
            events = events + AccountCredited(transactionId, id, now(clock), amount, transactionId, source)
        )
    }

    fun debit(
        transactionId: UUID, amount: BigDecimal, currency: String, source: TransactionSource
    ): Either<AccountDebitFailed, Account> = either {
        ensure(currency == this@Account.currency.currencyCode) {
            AccountDebitFailed(transactionId, id, now(clock), AccountDebitFailed.Reason.INVALID_CURRENCY)
        }
        ensure(status == Opened) {
            AccountDebitFailed(transactionId, id, now(clock), AccountDebitFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS)
        }
        ensure(amount > ZERO) {
            AccountDebitFailed(transactionId, id, now(clock), AccountDebitFailed.Reason.NON_POSITIVE_AMOUNT)
        }
        ensure(balance >= amount) {
            AccountDebitFailed(transactionId, id, now(clock), AccountDebitFailed.Reason.INSUFFICIENT_FUNDS)
        }
        copy(
            balance = balance - amount,
            revision = revision,
            events = events + AccountDebited(transactionId, id, now(clock), amount, transactionId, source)
        )
    }

    fun close(): Either<AccountClosingFailed, Account> = when {
        status == Closed -> AccountClosingFailed(generateId(), id, now(clock), AccountClosingFailed.Reason.ALREADY_CLOSED).left()
        balance > ZERO -> AccountClosingFailed(generateId(), id, now(clock), AccountClosingFailed.Reason.FUNDS_STILL_PRESENT).left()
        else -> copy(
            status = Closed,
            revision = revision,
            events = events + AccountClosed(generateId(), id, now(clock))
        ).right()
    }


    companion object {

        val generateId: () -> UUID = { UUID.randomUUID() }

        private val allowedCurrencies = listOf("EUR")

        private val allowedAccountTypes = listOf("MAIN")

        fun initiate(
            accountId: UUID,
            accountType: String,
            currency: String,
            customerId: UUID,
            clock: Clock = Clock.systemUTC()
        ): Either<AccountInitiationFailed, Account> = either {
            ensure(allowedCurrencies.contains(currency)) {
                AccountInitiationFailed(accountId, accountId, now(clock), INVALID_CURRENCY)
            }
            ensure(allowedAccountTypes.contains(accountType)) {
                AccountInitiationFailed(accountId, accountId, now(clock), INVALID_ACCOUNT_TYPE)
            }
            Account(
                id = accountId,
                revision = 0,
                customerId = customerId,
                currency = Currency.getInstance(currency),
                type = AccountType.valueOf(accountType),
                balance = ZERO,
                status = Initiated,
                events = listOf(AccountInitiated(accountId, accountId, now(clock), customerId, currency, accountType))
            )
        }
    }
}
