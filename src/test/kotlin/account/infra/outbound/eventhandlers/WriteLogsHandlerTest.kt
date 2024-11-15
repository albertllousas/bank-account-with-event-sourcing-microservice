package account.infra.outbound.eventhandlers

import account.fixtures.TestBuilders
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.helpers.NOPLogger

@Tag("integration")
class WriteLogsHandlerTest {

    private val logger = spyk(NOPLogger.NOP_LOGGER)

    private val logEventWriter = WriteLogsHandler(logger)

    @Test
    fun `should write a log for any domain event`() {
        val event = TestBuilders.Events.buildAccountInitiated()

        logEventWriter.handleEvent(event)

        verify { logger.info("event-id: '${event.eventId}', event: 'AccountInitiated', account-id: '${event.accountId}'") }
    }

    @Test
    fun `should write a log for any domain error`() {
        val event = TestBuilders.Events.buildAccountInitiationFailed()

        logEventWriter.handleEvent(event)

        verify {
            logger.warn("event-id: '${event.eventId}', event: 'AccountInitiationFailed', account-id: '${event.accountId}', error: 'INVALID_ACCOUNT_TYPE'")
        }
    }

    @Test
    fun `should write a log for any domain event with extra info`() {
        val event = TestBuilders.Events.buildAccountCredited()

        logEventWriter.handleEvent(event)

        verify {
            logger.info("event-id: '${event.eventId}', event: 'AccountCredited', account-id: '${event.accountId}', source: 'SEPA_TRANSFER'")
        }
    }
}
