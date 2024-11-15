package account.app.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

sealed interface Event {
    val eventId: UUID
    val accountId: UUID
    val on: LocalDateTime
}

sealed interface DomainEvent : Event

sealed interface AccountError : Event

data class AccountInitiated(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val customerId: UUID,
    val currency: String,
    val type: String,
) : DomainEvent


data class AccountInitiationFailed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val reason: Reason,
) : AccountError {
    enum class Reason { INVALID_CURRENCY, INVALID_ACCOUNT_TYPE }
}

data class AccountOpened(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
) : DomainEvent

data class AccountOpeningFailed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val reason: Reason,
) : AccountError {
    enum class Reason { ALREADY_OPENED, ACCOUNT_NOT_ON_VALID_STATUS }
}

data class AccountCredited(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val amount: BigDecimal,
    val transactionId: UUID,
    val transactionSource: TransactionSource,
) : DomainEvent

data class AccountCreditFailed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val reason: Reason,
) : AccountError {
    enum class Reason { INVALID_CURRENCY, NON_POSITIVE_AMOUNT, ACCOUNT_NOT_ON_VALID_STATUS }
}

data class AccountDebited(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val amount: BigDecimal,
    val transactionId: UUID,
    val transactionSource: TransactionSource,
) : DomainEvent

data class AccountDebitFailed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val reason: Reason,
) : AccountError {
    enum class Reason { INVALID_CURRENCY, NON_POSITIVE_AMOUNT, ACCOUNT_NOT_ON_VALID_STATUS, INSUFFICIENT_FUNDS }
}

data class AccountClosed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
) : DomainEvent

data class AccountClosingFailed(
    override val eventId: UUID,
    override val accountId: UUID,
    override val on: LocalDateTime,
    val reason: Reason,
) : AccountError {
    enum class Reason { ALREADY_CLOSED, FUNDS_STILL_PRESENT }
}
