package account.infra.outbound.eventhandlers

import account.app.domain.Account
import account.app.domain.AccountClosed
import account.app.domain.AccountCredited
import account.app.domain.AccountDebited
import account.app.domain.AccountOpened
import account.app.domain.AccountError
import account.app.domain.AccountInitiated
import account.app.domain.AccountInitiationFailed
import account.app.domain.AccountStatus.*
import account.app.domain.AccountType
import account.app.domain.Event
import account.infra.outbound.PostgresAccountProjectionRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal.ZERO
import java.util.Currency

@Component
class UpdateAccountProjectionHandler(private val repository: PostgresAccountProjectionRepository) {

    fun handleEvent(event: Event, newRevision: Long) {
        when (event) {
            is AccountInitiated -> event.toNewAccount()
            is AccountInitiationFailed -> null
            is AccountError -> repository.find(event.accountId, preventStaleReads = false)
            is AccountOpened -> repository.find(event.accountId, preventStaleReads = false).copy(status = Opened)
            is AccountCredited -> repository.find(event.accountId, preventStaleReads = false).let {
                it.copy(balance = it.balance + event.amount)
            }
            is AccountDebited -> repository.find(event.accountId, preventStaleReads = false).let {
                it.copy(balance = it.balance - event.amount)
            }

            is AccountClosed -> repository.find(event.accountId, preventStaleReads = false).copy(status = Closed)
        }?.withVersion(newRevision)
            ?.also {
                if (event is AccountInitiated) repository.create(it, event.eventId)
                else repository.update(it, event.eventId)
            }
    }

    private fun AccountInitiated.toNewAccount() = Account(
        accountId, customerId, Currency.getInstance(currency), AccountType.valueOf(type), 0, Initiated, ZERO
    )

    private fun Account.withVersion(newRevision: Long) = copy(revision = newRevision)
}
