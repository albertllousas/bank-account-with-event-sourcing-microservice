package account.app.usecases

import account.app.domain.AccountOpeningFailed.Reason.ALREADY_OPENED
import account.app.domain.OutboundPorts
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OpenAccountUseCase(
    private val accountReadRepository: OutboundPorts.AccountReadRepository,
    private val accountWriteRepository: OutboundPorts.AccountWriteRepository,
    private val errorEventReporter: OutboundPorts.ErrorEventReporter
) {

    operator fun invoke(accountId: UUID) {
        accountReadRepository.find(accountId)
            .open()
            .onRight { accountWriteRepository.save(it) }
            .onLeft { if (it.reason != ALREADY_OPENED) errorEventReporter.report(it) }
    }
}
