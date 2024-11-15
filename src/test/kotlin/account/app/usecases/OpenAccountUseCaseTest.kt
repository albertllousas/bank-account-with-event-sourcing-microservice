package account.app.usecases

import account.app.domain.AccountOpened
import account.app.domain.AccountOpeningFailed
import account.app.domain.AccountStatus.Closed
import account.app.domain.AccountStatus.Initiated
import account.app.domain.AccountStatus.Opened
import account.app.domain.OutboundPorts
import account.fixtures.TestBuilders
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test

class OpenAccountUseCaseTest {

    private val accountReadRepository = mockk<OutboundPorts.AccountReadRepository>()

    private val accountWriteRepository = mockk<OutboundPorts.AccountWriteRepository>(relaxed = true)

    private val errorEventReporter = mockk<OutboundPorts.ErrorEventReporter>(relaxed = true)

    private val createAccount = OpenAccountUseCase(accountReadRepository, accountWriteRepository, errorEventReporter)

    @Test
    fun `should open an account successfully`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(status = Initiated)

        createAccount(accountId)

        verify { accountWriteRepository.save(match { it.events[0] is AccountOpened }) }
    }

    @Test
    fun `should not report an error when account is already opened`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(status = Opened)

        createAccount(accountId)

        verify { errorEventReporter wasNot Called }
    }

    @Test
    fun `should report an error when account opening fails`() {
        val accountId = UUID.randomUUID()
        every { accountReadRepository.find(accountId) } returns TestBuilders.Account.build(status = Closed)

        createAccount(accountId)

        verify { errorEventReporter.report(any<AccountOpeningFailed>()) }
    }
}