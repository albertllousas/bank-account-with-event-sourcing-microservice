package account.app.usecases

import account.app.domain.AccountClosingFailed.Reason.ALREADY_CLOSED
import account.app.domain.OutboundPorts
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CloseAccountUseCase(
    private val accountReadRepository: OutboundPorts.AccountReadRepository,
    private val accountWriteRepository: OutboundPorts.AccountWriteRepository,
    private val errorEventReporter: OutboundPorts.ErrorEventReporter
) {

    operator fun invoke(accountId: UUID) {
        accountReadRepository.find(accountId)
            .close()
            .onRight(accountWriteRepository::save)
            .onLeft { if (it.reason != ALREADY_CLOSED) errorEventReporter.report(it) }
    }
}