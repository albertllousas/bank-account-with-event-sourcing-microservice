package account.infra.inbound

import account.app.usecases.OpenAccountUseCase
import account.fixtures.TestBuilders
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class TriggerUseCaseAccountEventHandlerTest {

    private val createAccount = mockk<OpenAccountUseCase>(relaxed = true)

    private val triggerUseCaseAccountEventHandler = TriggerUseCaseAccountEventHandler(createAccount)

    @Test
    fun `should trigger create account usecase when account initiated event is received`() {
        val accountId = UUID.randomUUID()

        triggerUseCaseAccountEventHandler.handleEvent(TestBuilders.Events.buildAccountInitiated(accountId = accountId))

        verify { createAccount(accountId) }
    }

    @Test
    fun `should do nothing when account error event is received`() {
        triggerUseCaseAccountEventHandler.handleEvent(TestBuilders.Events.buildAccountInitiationFailed())

        verify(exactly = 0) { createAccount(any()) }
    }
}
