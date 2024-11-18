package account.app.usecases

import account.app.domain.AccountCreditFailed
import account.app.domain.AccountCredited
import account.app.domain.AccountStatus.Opened
import account.app.domain.OutboundPorts
import account.app.domain.TransactionSource.SEPA_TRANSFER
import account.fixtures.TestBuilders
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test

class CreditAccountUseCaseTest {

    private val accountWriteRepository = mockk<OutboundPorts.AccountWriteRepository>(relaxed = true)

    private val accountReadRepository = mockk<OutboundPorts.AccountReadRepository>(relaxed = true)

    private val errorEventReporter = mockk<OutboundPorts.ErrorEventReporter>(relaxed = true)

    private val creditAccount = CreditAccountUseCase(accountReadRepository, accountWriteRepository, errorEventReporter)

    @Test
    fun `should credit an account successfully`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(status = Opened)

        creditAccount(UUID.randomUUID(), accountId, BigDecimal(100), "EUR", SEPA_TRANSFER)

        verify { accountWriteRepository.save(match { it.newEvents[0] is AccountCredited }) }
    }

    @Test
    fun `should report an error when crediting an account fails`() {
        every { accountReadRepository.find(any()) } returns TestBuilders.Account.build()

        creditAccount(UUID.randomUUID(), UUID.randomUUID(), BigDecimal(100), "USD", SEPA_TRANSFER)

        verify { errorEventReporter.report(any<AccountCreditFailed>()) }
    }
}