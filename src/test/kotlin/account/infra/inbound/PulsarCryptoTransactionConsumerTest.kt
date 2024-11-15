package account.infra.inbound

import account.app.domain.TransactionSource.CRYPTO_TX
import account.app.usecases.CreditAccountUseCase
import account.app.usecases.DebitAccountUseCase
import account.fixtures.containers.Pulsar
import account.infra.inbound.WalletOperation.RECEIVING
import account.infra.inbound.WalletOperation.SENDING
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
class PulsarCryptoTransactionConsumerTest {

    private val pulsar = Pulsar.getInstance()

    private val creditAccountFunds = mockk<CreditAccountUseCase>(relaxed = true)

    private val debitAccountFunds = mockk<DebitAccountUseCase>(relaxed = true)

    private lateinit var producer: Producer<CryptoTransactionEvent>

    private val consumer = PulsarCryptoTransactionConsumer(pulsar.client, creditAccountFunds, debitAccountFunds)

    @BeforeEach
    fun setup() {
        clearMocks(creditAccountFunds, debitAccountFunds)
        producer = pulsar.client
            .newProducer(Schema.JSON(CryptoTransactionEvent::class.java))
            .topic("crypto-transaction.events")
            .create()

    }

    @AfterEach
    fun tearDownAll() {
        pulsar.cleanup()
        consumer.close()
        producer.close()
    }

    @Test
    fun `should credit the account when a crypto transaction with wallet operation as receiving is confirmed`() {
        val event =
            CryptoTransactionEvent.Confirmed(UUID.randomUUID(), "confirmed", UUID.randomUUID(), TEN, "EUR", RECEIVING)

        producer.send(event)

        verify(timeout = 1000) {
            creditAccountFunds(event.transactionId, event.accountId, event.amount, event.currency, CRYPTO_TX)
        }
    }

    @Test
    fun `should debit the account when a crypto transaction with wallet operation as sending is initiated`() {
        val event =
            CryptoTransactionEvent.Initiated(UUID.randomUUID(), "initiated", UUID.randomUUID(), TEN, "EUR", SENDING)

        producer.send(event)

        verify(timeout = 1000) {
            debitAccountFunds(event.transactionId, event.accountId, event.amount, event.currency, CRYPTO_TX)
        }
    }

    @Test
    fun `should do nothing when a crypto transaction event is not relevant`() {
        val event = CryptoTransactionEvent.Signed(UUID.randomUUID(), "signed", UUID.randomUUID(), TEN, "EUR", SENDING)

        producer.send(event)

        Thread.sleep(500)
        verify(exactly = 0) {
            creditAccountFunds(any(), any(), any(), any(), any())
            debitAccountFunds(any(), any(), any(), any(), any())
        }
    }
}
