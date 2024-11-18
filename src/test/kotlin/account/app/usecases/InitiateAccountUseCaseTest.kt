package account.app.usecases

import account.app.domain.AccountInitiated
import account.app.domain.AccountInitiationFailed
import account.app.domain.OutboundPorts
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class InitiateAccountUseCaseTest {

    private val accountWriteRepository = mockk<OutboundPorts.AccountWriteRepository>(relaxed = true)

    private val errorEventReporter = mockk<OutboundPorts.ErrorEventReporter>(relaxed = true)

    private val usecase = InitiateAccountUseCase(accountWriteRepository, errorEventReporter)

    @Test
    fun `should orchestrate the initiation of an account successfully`() {
        val customerId = UUID.randomUUID()
        val accountId = UUID.randomUUID()

        usecase(accountId, "MAIN", "EUR", customerId)

        verify { accountWriteRepository.save(match { it.newEvents[0] is AccountInitiated }) }
    }

    @Test
    fun `should report an error when account initiation fails`() {
        usecase(UUID.randomUUID(), "SAVINGS", "EUR", UUID.randomUUID())

        verify { errorEventReporter.report(any<AccountInitiationFailed>()) }
    }
}
