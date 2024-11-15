package account.infra.outbound

import account.app.domain.Account
import account.app.domain.AccountStatus.Initiated
import account.app.domain.AccountStatus.Opened
import account.app.domain.AccountType
import account.app.domain.OutboundPorts
import account.infra.AccountNotFoundException
import account.infra.AccountNotUpToDateException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Isolation.REPEATABLE_READ
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.lang.invoke.MethodHandles
import java.math.BigDecimal
import java.util.Currency
import java.util.UUID

@Repository
class PostgresAccountProjectionRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val eventStoreAccountRepository: EventStoreAccountRepository,
    private val logger: Logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass()),
) : OutboundPorts.AccountReadRepository {

    @Transactional(propagation = REQUIRES_NEW)
    fun create(account: Account, eventId: UUID) {
        ensureIdempotency(eventId) {
            jdbcTemplate.update(
                """
                INSERT INTO account_projection (id, customer_id, currency, type, status, balance, revision, pending_out_of_order_updates)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                account.id,
                account.customerId,
                account.currency.currencyCode,
                account.type.name,
                account.status::class.simpleName,
                0,
                account.revision,
                0
            )
        }
    }

    @Transactional(propagation = REQUIRES_NEW, isolation = REPEATABLE_READ)
    fun update(account: Account, eventId: UUID) {
        try {
            ensureIdempotency(eventId) {
                val (currentRevision, currentPendingOutOfOrderUpdates) = jdbcTemplate.queryForObject(
                    "SELECT revision, pending_out_of_order_updates FROM account_projection WHERE id = ? FOR UPDATE",
                    { rs, _ -> Pair(rs.getLong("revision"), rs.getInt("pending_out_of_order_updates")) },
                    account.id
                ) ?: throw AccountNotUpToDateException(account.id)

                val newPendingOutOfOrderUpdates = calculatePendingOutOfOrderUpdates(
                    currentPendingOutOfOrderUpdates, currentRevision, account.revision
                )

                jdbcTemplate.update(
                    """
                UPDATE account_projection
                SET customer_id = ?,
                    currency = ?,
                    type = ?,
                    balance = ?,
                    status = ?,
                    revision = GREATEST(revision, ?),
                    pending_out_of_order_updates = ?
                WHERE id = ?
                """,
                    account.customerId,
                    account.currency.currencyCode,
                    account.type.name,
                    account.balance,
                    account.status::class.simpleName,
                    account.revision,
                    newPendingOutOfOrderUpdates,
                    account.id
                )
            }
        } catch (exception: EmptyResultDataAccessException) {
            throw AccountNotFoundException(account.id)
        }
    }

    fun find(accountId: UUID, preventStaleReads: Boolean): Account {
        try {
            val (account, pendingOutOfOrderUpdates) = jdbcTemplate.queryForObject(
                "SELECT * FROM account_projection WHERE id = ?",
                { rs, _ ->
                    Pair(
                        Account(
                            accountId,
                            rs.getObject("customer_id", UUID::class.java),
                            Currency.getInstance(rs.getString("currency")),
                            AccountType.valueOf(rs.getString("type")),
                            rs.getLong("revision"),
                            rs.getString("status").let { status ->
                                when (status) {
                                    "Initiated" -> Initiated
                                    "Opened" -> Opened
                                    else -> throw IllegalStateException("Unknown account status: $status")
                                }
                            },
                            BigDecimal.valueOf(rs.getLong("balance"))
                        ),
                        rs.getInt("pending_out_of_order_updates") != 0
                    )
                },
                accountId
            )!!
            if(preventStaleReads) preventStaleReads(account, pendingOutOfOrderUpdates)
            return account
        } catch (exception: EmptyResultDataAccessException) {
            throw AccountNotFoundException(accountId)
        }
    }

    /**
     *  Prevent possible stale reads, but they can still happen due edge cases with race conditions,
     *  for those cases the system will fail due optimistic locking mechanism in place in event store DB
     */
    private fun preventStaleReads(account: Account, pendingOutOfOrderUpdates: Boolean) {
        if (pendingOutOfOrderUpdates)
            throw AccountNotUpToDateException(account.id)
        else if(eventStoreAccountRepository.getRevisionFor(account.id) > account.revision)
            throw AccountNotUpToDateException(account.id)
    }

    private fun ensureIdempotency(eventId: UUID, action: () -> Unit) = try {
        jdbcTemplate.update("INSERT INTO idempotency_key (key) VALUES (?)", eventId)
        action()
    } catch (exception: DuplicateKeyException) {
        logger.warn("Event '$eventId' was already processed, skipping projection update")
    }

    private fun calculatePendingOutOfOrderUpdates(
        currentPendingOutOfOrderUpdates: Int,
        currentRevision: Long,
        newRevision: Long
    ): Long {
        val isOutOfOrderInPast = newRevision < currentRevision
        val isOutOfOrderInFuture = newRevision > currentRevision + 1
        val newPendingOutOfOrderUpdates = when {
            isOutOfOrderInPast -> maxOf(currentPendingOutOfOrderUpdates - 1L, 0)
            isOutOfOrderInFuture -> currentPendingOutOfOrderUpdates + (newRevision - (currentRevision + 1))
            else -> 0
        }
        return newPendingOutOfOrderUpdates
    }

    override fun find(accountId: UUID): Account = find(accountId, preventStaleReads = true)
}
