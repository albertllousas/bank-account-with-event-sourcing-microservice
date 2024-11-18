package account.app.usecases

import account.app.domain.AccountDebitFailed
import account.app.domain.AccountDebited
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

class DebitAccountUseCaseTest {

    private val accountWriteRepository = mockk<OutboundPorts.AccountWriteRepository>(relaxed = true)

    private val accountReadRepository = mockk<OutboundPorts.AccountReadRepository>(relaxed = true)

    private val errorEventReporter = mockk<OutboundPorts.ErrorEventReporter>(relaxed = true)

    private val debitAccount = DebitAccountUseCase(accountReadRepository, accountWriteRepository, errorEventReporter)

    @Test
    fun `should debit an account successfully`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(
            balance = 1000.toBigDecimal(), status = Opened
        )

        debitAccount(UUID.randomUUID(), accountId, BigDecimal(100), "EUR", SEPA_TRANSFER)

        verify { accountWriteRepository.save(match { it.newEvents[0] is AccountDebited }) }
    }

    @Test
    fun `should report an error when crediting an account fails`() {
        every { accountReadRepository.find(any()) } returns TestBuilders.Account.build()

        debitAccount(UUID.randomUUID(), UUID.randomUUID(), BigDecimal(100), "USD", SEPA_TRANSFER)

        verify { errorEventReporter.report(any<AccountDebitFailed>()) }
    }
}