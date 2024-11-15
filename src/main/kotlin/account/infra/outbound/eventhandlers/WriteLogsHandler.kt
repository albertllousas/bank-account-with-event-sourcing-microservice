package account.infra.outbound.eventhandlers

import account.app.domain.AccountClosingFailed
import account.app.domain.AccountCreditFailed
import account.app.domain.AccountCredited
import account.app.domain.AccountDebitFailed
import account.app.domain.AccountDebited
import account.app.domain.AccountError
import account.app.domain.AccountInitiationFailed
import account.app.domain.AccountOpeningFailed
import account.app.domain.DomainEvent
import account.app.domain.Event
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.lang.invoke.MethodHandles

@Component
class WriteLogsHandler(
    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()),
) {

    fun handleEvent(event: Event) {
        val msg = "event-id: '${event.eventId}', event: '${event::class.simpleName}', account-id: '${event.accountId}'"
        if (event is DomainEvent) logger.info(msg + extraInfo(event))
        else logger.warn("$msg, error: '${(event as AccountError).error()}'")
    }

    private fun extraInfo(event: DomainEvent) = when (event) {
        is AccountCredited -> ", source: '${event.transactionSource}'"
        is AccountDebited -> ", source: '${event.transactionSource}'"
        else -> ""
    }

    private fun AccountError.error() = when (this) {
        is AccountInitiationFailed -> reason.name
        is AccountOpeningFailed -> reason.name
        is AccountCreditFailed -> reason.name
        is AccountDebitFailed -> reason.name
        is AccountClosingFailed -> reason.name
    }
}
