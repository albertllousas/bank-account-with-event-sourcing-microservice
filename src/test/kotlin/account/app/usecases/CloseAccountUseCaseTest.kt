package account.app.usecases

import account.app.domain.AccountClosed
import account.app.domain.AccountClosingFailed
import account.app.domain.AccountStatus.Closed
import account.app.domain.OutboundPorts
import account.fixtures.TestBuilders
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal.TEN
import java.util.UUID
import kotlin.test.Test

class CloseAccountUseCaseTest {

    private val accountReadRepository = mockk<OutboundPorts.AccountReadRepository>()

    private val accountWriteRepository = mockk<OutboundPorts.AccountWriteRepository>(relaxed = true)

    private val errorEventReporter = mockk<OutboundPorts.ErrorEventReporter>(relaxed = true)

    private val closeAccount = CloseAccountUseCase(accountReadRepository, accountWriteRepository, errorEventReporter)

    @Test
    fun `should close an account successfully`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build()

        closeAccount(accountId)

        verify { accountWriteRepository.save(match { it.newEvents[0] is AccountClosed }) }
    }

    @Test
    fun `should not report an error when account is already closed`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(status = Closed)

        closeAccount(accountId)

        verify { errorEventReporter wasNot Called }
    }

    @Test
    fun `should report an error when account closing fails`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(balance = TEN)

        closeAccount(accountId)

        verify { errorEventReporter.report(any<AccountClosingFailed>()) }
    }
}