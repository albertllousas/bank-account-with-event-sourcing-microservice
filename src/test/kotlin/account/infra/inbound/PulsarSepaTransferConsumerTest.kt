package account.infra.inbound

import account.app.domain.TransactionSource.SEPA_TRANSFER
import account.app.usecases.CreditAccountUseCase
import account.app.usecases.DebitAccountUseCase
import account.fixtures.containers.Pulsar
import account.infra.inbound.Direction.INCOMING
import account.infra.inbound.Direction.OUTGOING
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.apache.pulsar.client.api.Producer
import org.apache.pulsar.client.api.ProducerAccessMode
import org.apache.pulsar.client.api.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import java.math.BigDecimal.TEN
import java.util.UUID
import kotlin.test.Test

@Tag("integration")
class PulsarSepaTransferConsumerTest {

    private val pulsar = Pulsar.getInstance()

    private val creditAccountFunds = mockk<CreditAccountUseCase>(relaxed = true)

    private val debitAccountFunds = mockk<DebitAccountUseCase>(relaxed = true)

    private val producer: Producer<SepaTransferEvent> by lazy {
        pulsar
            .client
            .newProducer(Schema.JSON(SepaTransferEvent::class.java))
            .topic("sepa-transfers.events")
            .accessMode(ProducerAccessMode.Shared)
            .create()
    }

    private val consumer = PulsarSepaTransferConsumer(pulsar.client, creditAccountFunds, debitAccountFunds)

    @BeforeEach
    fun setup() {
        clearMocks(creditAccountFunds, debitAccountFunds)
    }

    @AfterEach
    fun tearDownAll() {
        pulsar.cleanup()
        consumer.close()
        producer.close()
    }

    @Test
    fun `should credit the account when a sepa incoming transfer is accepted`() {
        val event = SepaTransferEvent.Accepted(UUID.randomUUID(), "accepted", UUID.randomUUID(), TEN, "EUR", INCOMING)

        producer.send(event)

        verify(timeout = 1000) {
            creditAccountFunds(event.transactionId, event.accountId, event.amount, event.currency, SEPA_TRANSFER)
        }
    }

    @Test
    fun `should debit the account when a sepa outgoing transfer is accepted`() {
        val event = SepaTransferEvent.Accepted(UUID.randomUUID(), "accepted", UUID.randomUUID(), TEN, "EUR", OUTGOING)

        producer.send(event)

        verify(timeout = 1000) {
            debitAccountFunds(event.transactionId, event.accountId, event.amount, event.currency, SEPA_TRANSFER)
        }
    }

    @Test
    fun `should not credit the account when a sepa outgoing transfer is not relevant`() {
        producer.send(SepaTransferEvent.Initiated(UUID.randomUUID(), "initiated", UUID.randomUUID(), TEN, "EUR"))

        Thread.sleep(500)
        verify(exactly = 0) { creditAccountFunds(any(), any(), any(), any(), any()) }
    }
}
