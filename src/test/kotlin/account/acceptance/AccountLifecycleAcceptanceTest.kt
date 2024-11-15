package account.acceptance

import account.fixtures.containers.Pulsar
import account.infra.inbound.CardTransactionEvent
import account.infra.inbound.CryptoTransactionEvent
import account.infra.inbound.Direction.INCOMING
import account.infra.inbound.Direction.OUTGOING
import account.infra.inbound.SepaTransferEvent
import account.infra.inbound.WalletOperation.RECEIVING
import account.infra.inbound.WalletOperation.SENDING
import account.infra.outbound.eventhandlers.ExternalAccountEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountClosedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountCreditedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountDebitedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountInitiatedEvent
import account.infra.outbound.eventhandlers.ExternalAccountEvent.AccountOpenedEvent
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured.given
import io.restassured.http.ContentType.JSON
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.util.UUID
import kotlin.test.Test

@Tag("acceptance")
//@Disabled
class AccountLifecycleAcceptanceTest : BaseAcceptanceTest() {

    @Test
    fun `should complete an account lifecycle`() {
        runBlocking {
            val consumer = pulsar.client.newConsumer().topic("account.events").subscriptionName("test").subscribe()
            val accountId = UUID.randomUUID()
            given().contentType(JSON).port(servicePort)
                .body(
                    """ { 
                    "accountId":"$accountId", 
                    "customerId":"${UUID.randomUUID()}", 
                    "accountType": "MAIN", 
                    "currency": "EUR"
                    } """
                )
                .`when`().post("/accounts").then()
                .assertThat().statusCode(202)

            delay(500)
            publish(
                topic = "sepa-transfers.events",
                event = SepaTransferEvent.Accepted(UUID.randomUUID(), "accepted", accountId, TEN, "EUR", INCOMING)
            )
            publish(
                topic = "crypto-transaction.events",
                event = CryptoTransactionEvent.Confirmed(UUID.randomUUID(), "confirmed", accountId, TEN, "EUR", RECEIVING)
            )
            delay(500)
            publish(
                topic = "sepa-transfers.events",
                event = SepaTransferEvent.Accepted(UUID.randomUUID(), "accepted", accountId, ONE, "EUR", OUTGOING)
            )
            publish(
                topic = "crypto-transaction.events",
                event = CryptoTransactionEvent.Initiated(UUID.randomUUID(), "initiated", accountId, ONE, "EUR", SENDING)
            )
            publish(
                topic = "card-transaction.events",
                event = CardTransactionEvent.Authorized(UUID.randomUUID(), "authorized", accountId, 18.toBigDecimal(), "EUR")
            )
            delay(500)
            given().port(servicePort).`when`().delete("/accounts/$accountId").then()
                .assertThat().statusCode(202)

            val messages = Pulsar.drain(consumer, 8)
            messages.map { mapper.readValue<ExternalAccountEvent>(it.data)::class } shouldBe listOf(
                AccountInitiatedEvent::class,
                AccountOpenedEvent::class,
                AccountCreditedEvent::class,
                AccountCreditedEvent::class,
                AccountDebitedEvent::class,
                AccountDebitedEvent::class,
                AccountDebitedEvent::class,
                AccountClosedEvent::class
            )
        }
    }

    private fun <T> publish(topic: String, event: T) {
        pulsar.client.newProducer().topic(topic)
            .create()
            .newMessage()
            .key(UUID.randomUUID().toString())
            .value(mapper.writeValueAsBytes(event))
            .send()
    }
}
