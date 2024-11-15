package account.infra.inbound

import account.app.domain.AccountClosed
import account.app.domain.AccountCredited
import account.app.domain.AccountDebited
import account.app.domain.AccountOpened
import account.app.domain.AccountError
import account.app.domain.AccountInitiated
import account.app.domain.Event
import account.app.usecases.OpenAccountUseCase
import org.springframework.stereotype.Component

@Component
class TriggerUseCaseAccountEventHandler(private val openAccount: OpenAccountUseCase) {

    fun handleEvent(event: Event) {
        when (event) {
            is AccountInitiated -> openAccount(event.accountId)
            is AccountOpened -> doNothing()
            is AccountCredited -> doNothing()
            is AccountDebited -> doNothing()
            is AccountError -> doNothing()
            is AccountClosed -> doNothing()
        }
        // TODO: retries in case of OptimisticLockException
    }
}

val doNothing: () -> Unit = {}