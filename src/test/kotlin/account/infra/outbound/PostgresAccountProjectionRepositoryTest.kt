package account.infra.outbound

import account.fixtures.TestBuilders
import account.fixtures.containers.Postgres
import account.infra.AccountNotFoundException
import account.infra.AccountNotUpToDateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.slf4j.helpers.NOPLogger
import java.math.BigDecimal.TEN
import java.math.BigDecimal.ZERO
import java.util.UUID
import kotlin.test.Test

@Tag("integration")
class PostgresAccountProjectionRepositoryTest {

    private val db = Postgres()

    private val logger = spyk(NOPLogger.NOP_LOGGER)

    private val eventStore = mockk<EventStoreAccountRepository>()

    private val repository = PostgresAccountProjectionRepository(db.jdbcTemplate, eventStore, logger)

    @AfterEach
    fun tearDown() {
        db.container.stop()
    }

    @Test
    fun `should save account projection`() {
        val account = TestBuilders.Account.build()

        repository.create(account, UUID.randomUUID())

        val result =
            db.jdbcTemplate.queryForMap("SELECT * FROM account_projection WHERE id = ?", account.id) - "created_at"

        result shouldBe mapOf(
            "id" to account.id,
            "balance" to ZERO,
            "status" to account.status::class.simpleName,
            "currency" to account.currency.currencyCode,
            "customer_id" to account.customerId,
            "type" to account.type.name,
            "revision" to account.revision,
            "pending_out_of_order_updates" to 0
        )
    }

    @Test
    fun `should not update an account when event was already processed`() {
        val account = TestBuilders.Account.build()
        val eventId = UUID.randomUUID()

        repository.create(account, eventId)
        repository.create(account, eventId)

        verify { logger.warn("Event '$eventId' was already processed, skipping projection update") }
    }

    @Test
    fun `should find an account projection`() {
        val account = TestBuilders.Account.build()
        every { eventStore.getRevisionFor(account.id) } returns 0
        repository.create(account, UUID.randomUUID())

        val result = repository.find(account.id)

        result shouldBe account
    }

    @Test
    fun `should throw an exception when account is not found`() {
        shouldThrow<AccountNotFoundException> { repository.find(UUID.randomUUID()) }
    }

    @Test
    fun `should throw an exception when accessing an account that is not up to date`() {
        val account = TestBuilders.Account.build()
        repository.create(account, UUID.randomUUID())
        db.jdbcTemplate.update("UPDATE account_projection SET pending_out_of_order_updates = 1 WHERE id = ?", account.id)

        shouldThrow<AccountNotUpToDateException> { repository.find(account.id, true) }
    }

    @Test
    fun `should update an account projection`() {
        val account = TestBuilders.Account.build()
        repository.create(account, UUID.randomUUID())
        every { eventStore.getRevisionFor(account.id) } returns 0

        val updatedAccount = account.copy(balance = TEN, revision = 1)

        repository.update(updatedAccount, UUID.randomUUID())

        val result = repository.find(account.id)

        result shouldBe updatedAccount
    }

    @Test
    fun `should fail updating an account that does not exist`() {
        val account = TestBuilders.Account.build()

        shouldThrow<AccountNotFoundException> { repository.update(account, UUID.randomUUID()) }
    }

    @Test
    fun `should update an account projection and keep track of our of order events`() {
        val account = TestBuilders.Account.build()
        repository.create(account, UUID.randomUUID())

        repository.update(account.copy(balance = TEN, revision = 3), UUID.randomUUID())
        getRevisionAndPendingOutOfOrderUpdates(account.id) shouldBe Pair(3L, 2) // (revision, pending_out_of_order_updates)

        repository.update(account.copy(balance = TEN, revision = 5), UUID.randomUUID())
        getRevisionAndPendingOutOfOrderUpdates(account.id) shouldBe Pair(5L, 3)

        repository.update(account.copy(balance = TEN, revision = 2), UUID.randomUUID())
        getRevisionAndPendingOutOfOrderUpdates(account.id) shouldBe Pair(5L, 2)

        repository.update(account.copy(balance = TEN, revision = 1), UUID.randomUUID())
        getRevisionAndPendingOutOfOrderUpdates(account.id) shouldBe Pair(5L, 1)

        repository.update(account.copy(balance = TEN, revision = 4), UUID.randomUUID())
        getRevisionAndPendingOutOfOrderUpdates(account.id) shouldBe Pair(5L, 0)
    }

    private fun getRevisionAndPendingOutOfOrderUpdates(accountId: UUID) =
        db.jdbcTemplate.queryForObject(
            "SELECT revision, pending_out_of_order_updates FROM account_projection WHERE id = ?",
            { rs, _ -> Pair(rs.getLong("revision"), rs.getInt("pending_out_of_order_updates")) },
            accountId
        )
}