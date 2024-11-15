package account.infra

import java.util.UUID

data class AccountNotFoundException(val accountId: UUID) : RuntimeException("Account with id '$accountId' not found")

data class AccountNotUpToDateException(val accountId: UUID) : RuntimeException("Account with id '$accountId' is not up to date")

data class OptimisticLockException(val accountId: UUID, val eventId: UUID) : RuntimeException(
    "Optimistic lock exception for account with id '$accountId' and event with id '$eventId'"
)
