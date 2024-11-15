package account.infra.inbound

import account.app.domain.TransactionSource.CARD_TX
import account.app.usecases.DebitAccountUseCase
import account.fixtures.containers.Pulsar
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import java.math.BigDecimal.TEN
import java.util.UUID
import kotlin.test.Test

@Tag("integration")
class PulsarCardTransactionConsumerTest {

    private val pulsar = Pulsar.getInstance()

    private val debitAccountFunds = mockk<DebitAccountUseCase>(relaxed = true)

    private lateinit var producer: Producer<CardTransactionEvent>



    private val consumer = PulsarCardTransactionConsumer(pulsar.client, debitAccountFunds)

    @BeforeEach
    fun setup() {
        clearMocks(debitAccountFunds)
        producer = pulsar.client
            .newProducer(Schema.JSON(CardTransactionEvent::class.java))
            .topic("card-transaction.events")
            .create()
    }

    @AfterEach
    fun tearDownAll() {
        pulsar.cleanup()
//        consumer.close()
//        producer.close()
    }

    @Test
    fun `should credit the account when a card transaction is authorized`() {
        val event = CardTransactionEvent.Authorized(UUID.randomUUID(), "authorized", UUID.randomUUID(), TEN, "EUR")

        producer.send(event)

        verify(timeout = 1000) {
            debitAccountFunds(event.transactionId, event.accountId, event.amount, event.currency, CARD_TX)
        }
    }

    @Test
    fun `should debit the account when a card transaction event is not relevant`() {
        val event = CardTransactionEvent.Initiated(UUID.randomUUID(), "initiated", UUID.randomUUID(), TEN, "EUR")

        producer.send(event)

        Thread.sleep(500)
        verify(timeout = 1000, exactly = 0) {
            debitAccountFunds(any(), any(), any(), any(), any())
        }
    }
}
