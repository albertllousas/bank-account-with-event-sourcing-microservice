package account.infra.outbound

import account.app.domain.Account
import account.app.domain.AccountError
import account.app.domain.AccountInitiated
import account.app.domain.OutboundPorts
import account.infra.OptimisticLockException
import com.eventstore.dbclient.AppendToStreamOptions
import com.eventstore.dbclient.EventData
import com.eventstore.dbclient.EventStoreDBClient
import com.eventstore.dbclient.ExpectedRevision.noStream
import com.eventstore.dbclient.ReadStreamOptions
import com.eventstore.dbclient.WrongExpectedVersionException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Repository
import java.util.UUID
import java.util.concurrent.ExecutionException

@Repository
class EventStoreAccountRepository(
    private val eventStoreDBClient: EventStoreDBClient,
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
) : OutboundPorts.AccountWriteRepository, OutboundPorts.ErrorEventReporter {

    companion object {
        val ACCOUNT_EVENTS_STREAM_PREFIX = "account-"
    }

    override fun save(account: Account) {
        try {
            if (account.newEvents.isEmpty()) return

            val revision =
                if (account.newEvents.first() is AccountInitiated) AppendToStreamOptions.get().expectedRevision(noStream())
                else AppendToStreamOptions.get().expectedRevision(account.revision)

            eventStoreDBClient.appendToStream(
                "$ACCOUNT_EVENTS_STREAM_PREFIX${account.id}",
                revision,
                account.newEvents.map { toEventData(it) }.toTypedArray().iterator()
            ).get()
        } catch (e: ExecutionException) {
            if(e.cause is WrongExpectedVersionException) throw OptimisticLockException(account.id, account.newEvents.first().eventId) else throw e
        }
    }

    override fun report(event: AccountError) {
        eventStoreDBClient.appendToStream("$ACCOUNT_EVENTS_STREAM_PREFIX${event.accountId}", toEventData(event)).get()
    }

    private fun <T: Any> toEventData(obj: T) =
        EventData.builderAsJson(obj::class.simpleName!!, mapper.writeValueAsBytes(obj)).eventId(UUID.randomUUID()).build()

    fun getRevisionFor(account: UUID): Long =
        eventStoreDBClient.readStream(
            "$ACCOUNT_EVENTS_STREAM_PREFIX$account",
            ReadStreamOptions.get().fromEnd().backwards().maxCount(1)
        ).get().events.first().event.revision
}
