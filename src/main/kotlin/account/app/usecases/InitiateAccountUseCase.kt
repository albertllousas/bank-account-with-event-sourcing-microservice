package account.app.usecases

import account.app.domain.Account
import account.app.domain.OutboundPorts
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class InitiateAccountUseCase(
    private val accountWriteRepository: OutboundPorts.AccountWriteRepository,
    private val errorEventReporter: OutboundPorts.ErrorEventReporter
) {

    operator fun invoke(accountId: UUID, accountType: String, currency: String, customerId: UUID) {
        Account.initiate(accountId, accountType, currency, customerId)
            .onRight(accountWriteRepository::save)
            .onLeft(errorEventReporter::report)
            .fold(ifLeft = { it.accountId }, ifRight = { it.id })
    }
}
