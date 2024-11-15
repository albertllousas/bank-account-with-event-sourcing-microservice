package account.infra.outbound.eventhandlers

import account.app.domain.Account
import account.app.domain.AccountStatus
import account.app.domain.AccountStatus.*
import account.app.domain.AccountType.MAIN
import account.fixtures.TestBuilders
import account.fixtures.TestBuilders.EURO
import account.infra.outbound.PostgresAccountProjectionRepository
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal.ZERO
import kotlin.test.Test

class UpdateAccountProjectionHandlerTest {

    private val repository = mockk<PostgresAccountProjectionRepository>(relaxed = true)

    private val updateAccountProjectionHandler = UpdateAccountProjectionHandler(repository)

    @Test
    fun `should create an account projection when event is AccountInitiated`() {
        val event = TestBuilders.Events.buildAccountInitiated()

        updateAccountProjectionHandler.handleEvent(event, 1)

        verify {
            with(event) {
                repository.create(Account(accountId, customerId, EURO, MAIN, 1, Initiated, ZERO), eventId)
            }
        }
    }

    @Test
    fun `should not create a projection when event is AccountInitiationFailed`() {
        val event = TestBuilders.Events.buildAccountInitiationFailed()

        updateAccountProjectionHandler.handleEvent(event, 1)

        verify { repository wasNot Called }
    }

    @Test
    fun ` should update the projection when event is account opened`() {
        val event = TestBuilders.Events.buildAccountOpened()
        val account = TestBuilders.Account.build(status = Initiated, revision = 0)
        every { repository.find(event.accountId, false) } returns account

        updateAccountProjectionHandler.handleEvent(event, 1)

        verify {
            with(event) {
                repository.update(account.copy(revision = 1, status = Opened), eventId)
            }
        }
    }

    @Test
    fun `should update the projection when event is account credited`() {
        val event = TestBuilders.Events.buildAccountCredited(amount = 10.toBigDecimal())
        val account = TestBuilders.Account.build(balance = 11.toBigDecimal(), revision = 22)
        every { repository.find(event.accountId, false) } returns account

        updateAccountProjectionHandler.handleEvent(event, 23)

        verify {
            with(event) {
                repository.update(account.copy(balance = 21.toBigDecimal(), revision = 23), eventId)
            }
        }
    }

    @Test
    fun `should update the projection when event is account debited`() {
        val event = TestBuilders.Events.buildAccountDebited(amount = 10.toBigDecimal())
        val account = TestBuilders.Account.build(balance = 11.toBigDecimal(), revision = 22)
        every { repository.find(event.accountId, false) } returns account

        updateAccountProjectionHandler.handleEvent(event, 23)

        verify {
            with(event) {
                repository.update(account.copy(balance = 1.toBigDecimal(), revision = 23), eventId)
            }
        }
    }

    @Test
    fun `should update the projection when event is account closed`() {
        val event = TestBuilders.Events.buildAccountClosed()
        val account = TestBuilders.Account.build(status = Initiated, revision = 0)
        every { repository.find(event.accountId, false) } returns account

        updateAccountProjectionHandler.handleEvent(event, 1)

        verify {
            with(event) {
                repository.update(account.copy(revision = 1, status = Closed), eventId)
            }
        }
    }
}
