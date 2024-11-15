package account.infra.outbound.eventhandlers

import account.app.domain.AccountClosingFailed
import account.app.domain.AccountCreditFailed
import account.app.domain.AccountDebitFailed
import account.app.domain.AccountError
import account.app.domain.AccountInitiationFailed
import account.app.domain.AccountOpeningFailed
import account.app.domain.DomainEvent
import account.app.domain.Event
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.springframework.stereotype.Component

@Component
class PublishMetricsHandler(private val metrics: MeterRegistry) {

    fun handleEvent(event: Event) {
        val tags = listOf(Tag.of("type", event::class.simpleName!!))
        when (event) {
            is DomainEvent -> metrics.counter("domain.event", tags).increment()
            is AccountError -> metrics.counter("domain.error", tags + Tag.of("error", event.error())).increment()
        }
    }

    private fun AccountError.error(): String = when(this) {
        is AccountInitiationFailed -> reason.name
        is AccountOpeningFailed -> reason.name
        is AccountCreditFailed -> reason.name
        is AccountDebitFailed -> reason.name
        is AccountClosingFailed -> reason.name
    }
}
