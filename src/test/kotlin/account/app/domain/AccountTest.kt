package account.app.domain

import account.app.domain.AccountInitiationFailed.Reason.INVALID_ACCOUNT_TYPE
import account.app.domain.AccountInitiationFailed.Reason.INVALID_CURRENCY
import account.app.domain.AccountOpeningFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS
import account.app.domain.AccountOpeningFailed.Reason.ALREADY_OPENED
import account.app.domain.AccountStatus.Closed
import account.app.domain.AccountStatus.Initiated
import account.app.domain.AccountStatus.Opened
import account.app.domain.TransactionSource.*
import account.fixtures.TestBuilders
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigDecimal.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime.parse
import java.util.Currency
import java.util.UUID

class AccountTest {

    private val clock = Clock.fixed(Instant.parse("2021-01-01T00:00:00Z"), Clock.systemUTC().zone)

    @Nested
    inner class AccountInitiation {

        @Test
        fun `should initiate an account successfully`() {
            val newId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            val result = Account.initiate(newId, "MAIN", "EUR", customerId, clock)

            result shouldBe Account(
                newId,
                customerId,
                Currency.getInstance("EUR"),
                AccountType.MAIN,
                0,
                Initiated,
                ZERO,
                listOf(AccountInitiated(newId, newId, parse("2021-01-01T00:00:00"), customerId, "EUR", "MAIN"))
            ).right()
        }

        @Test
        fun `should fail to initiate an account with invalid currency`() {
            val newId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            val result = Account.initiate(newId, "MAIN", "USD", customerId, clock)

            result shouldBe AccountInitiationFailed(newId, newId, parse("2021-01-01T00:00:00"), INVALID_CURRENCY).left()
        }

        @Test
        fun `should fail to initiate an account with invalid account type`() {
            val newId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            val result = Account.initiate(newId, "SAVINGS", "EUR", customerId, clock)

            result shouldBe AccountInitiationFailed(newId, newId, parse("2021-01-01T00:00:00"), INVALID_ACCOUNT_TYPE).left()
        }
    }

    @Nested
    inner class AccountOpening {

        @Test
        fun `should open an account successfully`() {
            val eventId = UUID.randomUUID()
            val account =
                TestBuilders.Account.build(status = Initiated, revision = 0, clock = clock, eventId = { eventId })

            val result = account.open()

            result shouldBe account.copy(
                status = Opened,
                revision = 0,
                events = listOf(AccountOpened(eventId, account.id, parse("2021-01-01T00:00:00")))
            ).right()
        }

        @Test
        fun `should fail to open an already opened account`() {
            val eventId = UUID.randomUUID()
            val account =
                TestBuilders.Account.build(status = Opened, revision = 0, clock = clock, eventId = { eventId })

            val result = account.open()

            result shouldBe AccountOpeningFailed(eventId, account.id, parse("2021-01-01T00:00:00"), ALREADY_OPENED).left()
        }

        @Test
        fun `should fail to open an account that has not been initiated`() {
            val eventId = UUID.randomUUID()
            val account =
                TestBuilders.Account.build(status = Closed, revision = 0, clock = clock, eventId = { eventId })

            val result = account.open()

            result shouldBe AccountOpeningFailed(
                eventId, account.id, parse("2021-01-01T00:00:00"), ACCOUNT_NOT_ON_VALID_STATUS
            ).left()
        }
    }

    @Nested
    inner class AccountCrediting {

        @Test
        fun `should credit an account successfully`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.credit(transactionId, TEN, "EUR", SEPA_TRANSFER)

            result shouldBe account.copy(
                balance = 20.toBigDecimal(),
                revision = 0,
                events = listOf(
                    AccountCredited(transactionId, account.id, parse("2021-01-01T00:00:00"), TEN, transactionId, SEPA_TRANSFER)
                )
            ).right()
        }

        @Test
        fun `should fail to credit an account with invalid currency`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.credit(transactionId, TEN, "USD", SEPA_TRANSFER)

            result shouldBe AccountCreditFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountCreditFailed.Reason.INVALID_CURRENCY
            ).left()
        }

        @Test
        fun `should fail to credit an account with non positive amount`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.credit(transactionId, ZERO, "EUR", SEPA_TRANSFER)

            result shouldBe AccountCreditFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountCreditFailed.Reason.NON_POSITIVE_AMOUNT
            ).left()
        }

        @Test
        fun `should fail to credit an account that's not opened`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Closed, revision = 0, balance = TEN, clock = clock)

            val result = account.credit(transactionId, TEN, "EUR", SEPA_TRANSFER)

            result shouldBe AccountCreditFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountCreditFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS
            ).left()
        }
    }

    @Nested
    inner class AccountDebiting {

        @Test
        fun `should debit an account successfully`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.debit(transactionId, 5.toBigDecimal(), "EUR", SEPA_TRANSFER)

            result shouldBe account.copy(
                balance = 5.toBigDecimal(),
                revision = 0,
                events = listOf(
                    AccountDebited(transactionId, account.id, parse("2021-01-01T00:00:00"), 5.toBigDecimal(), transactionId, SEPA_TRANSFER)
                )
            ).right()
        }

        @Test
        fun `should fail to debit an account with invalid currency`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.debit(transactionId, 5.toBigDecimal(), "USD", SEPA_TRANSFER)

            result shouldBe AccountDebitFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountDebitFailed.Reason.INVALID_CURRENCY
            ).left()
        }

        @Test
        fun `should fail to debit an account with non positive amount`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.debit(transactionId, ZERO, "EUR", SEPA_TRANSFER)

            result shouldBe AccountDebitFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountDebitFailed.Reason.NON_POSITIVE_AMOUNT
            ).left()
        }

        @Test
        fun `should fail to debit an account that's not opened`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Closed, revision = 0, balance = TEN, clock = clock)

            val result = account.debit(transactionId, 5.toBigDecimal(), "EUR", SEPA_TRANSFER)

            result shouldBe AccountDebitFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountDebitFailed.Reason.ACCOUNT_NOT_ON_VALID_STATUS
            ).left()
        }

        @Test
        fun `should fail to debit an account with insufficient funds`() {
            val transactionId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock)

            val result = account.debit(transactionId, 15.toBigDecimal(), "EUR", SEPA_TRANSFER)

            result shouldBe AccountDebitFailed(
                transactionId, account.id, parse("2021-01-01T00:00:00"), AccountDebitFailed.Reason.INSUFFICIENT_FUNDS
            ).left()
        }
    }

    @Nested
    inner class AccountClosing {

        @Test
        fun `should close an account successfully`() {
            val eventId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = ZERO, clock = clock, eventId = { eventId })

            val result = account.close()

            result shouldBe account.copy(
                status = Closed,
                revision = 0,
                events = listOf(AccountClosed(eventId, account.id, parse("2021-01-01T00:00:00")))
            ).right()
        }

        @Test
        fun `should fail to close an already closed account`() {
            val eventId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Closed, revision = 0, balance = ZERO, clock = clock, eventId = { eventId })

            val result = account.close()

            result shouldBe AccountClosingFailed(eventId, account.id, parse("2021-01-01T00:00:00"), AccountClosingFailed.Reason.ALREADY_CLOSED).left()
        }

        @Test
        fun `should fail to close an account with funds still present`() {
            val eventId = UUID.randomUUID()
            val account = TestBuilders.Account.build(status = Opened, revision = 0, balance = TEN, clock = clock, eventId = { eventId })

            val result = account.close()

            result shouldBe AccountClosingFailed(eventId, account.id, parse("2021-01-01T00:00:00"), AccountClosingFailed.Reason.FUNDS_STILL_PRESENT).left()
        }
    }
}
