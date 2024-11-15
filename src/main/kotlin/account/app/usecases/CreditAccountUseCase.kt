package account.app.usecases

import account.app.domain.OutboundPorts
import account.app.domain.TransactionSource
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class CreditAccountUseCase(
    private val accountReadRepository: OutboundPorts.AccountReadRepository,
    private val accountWriteRepository: OutboundPorts.AccountWriteRepository,
    private val errorEventReporter: OutboundPorts.ErrorEventReporter
) {

    operator fun invoke(
        transactionId: UUID, accountId: UUID, amount: BigDecimal, currency: String, source: TransactionSource
    ) {
        accountReadRepository.find(accountId)
            .credit(transactionId, amount, currency, source)
            .onRight(accountWriteRepository::save)
            .onLeft(errorEventReporter::report)
        }
}
