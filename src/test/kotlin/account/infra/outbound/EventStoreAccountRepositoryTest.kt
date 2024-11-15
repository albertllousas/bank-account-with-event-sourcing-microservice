package account.infra.outbound

import account.fixtures.TestBuilders
import account.fixtures.TestBuilders.Events.buildAccountInitiated
import account.fixtures.containers.EventStoreDB
import account.infra.OptimisticLockException
import com.eventstore.dbclient.ReadStreamOptions
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import java.util.UUID
import kotlin.test.Test

@Tag("integration")
class EventStoreAccountRepositoryTest {

    private val eventStoreDB = EventStoreDB()

    private val repository = EventStoreAccountRepository(eventStoreDB.client)

    private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @AfterEach
    fun tearDown() {
        eventStoreDB.container.stop()
    }

    @Test
    fun `should save account events`() {
        val event = buildAccountInitiated()
        val account = TestBuilders.Account.build(id = event.accountId, events = listOf(event))

        repository.save(account)

        val events = eventStoreDB.client.readStream("account-${account.id}", ReadStreamOptions.get()).get().events
        events.map { it.event.eventData } shouldBe account.events.map { mapper.writeValueAsBytes(it) }
    }

    @Test
    fun `should fail to save account events when concurrent access conflict`() {
        val accountId = UUID.randomUUID()
        val account = TestBuilders.Account.build(id = accountId, events = listOf(buildAccountInitiated()))
        repository.save(account)

        shouldThrow<OptimisticLockException> {
            repository.save(account)
        }
    }

    @Test
    fun `should report account error`() {
        val event = TestBuilders.Events.buildAccountInitiationFailed()

        repository.report(event)

        val events = eventStoreDB.client.readStream("account-${event.accountId}", ReadStreamOptions.get()).get().events
        events.map { it.event.eventData } shouldBe listOf(mapper.writeValueAsBytes(event))
    }
}
