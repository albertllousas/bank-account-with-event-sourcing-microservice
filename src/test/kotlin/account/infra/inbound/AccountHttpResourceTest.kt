package account.infra.inbound

import account.app.usecases.CloseAccountUseCase
import account.app.usecases.InitiateAccountUseCase
import account.fixtures.TestBuilders
import account.infra.outbound.PostgresAccountProjectionRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@Tag("integration")
@WebMvcTest(AccountHttpResource::class)
class AccountHttpResourceTest(@Autowired private val mvc: MockMvc) {

    @MockkBean
    private lateinit var initiateAccount: InitiateAccountUseCase

    @MockkBean
    private lateinit var closeAccount: CloseAccountUseCase

    @MockkBean
    private lateinit var accountProjectionRepository: PostgresAccountProjectionRepository

    @Test
    fun `should initiate an account`() {
        val customerId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        every { initiateAccount.invoke(any(), any(), any(), any()) } returns Unit

        val response = mvc.perform(
            post("/accounts")
                .contentType("application/json")
                .content(""" { "accountId":"$accountId", "customerId":"$customerId", "accountType": "MAIN", "currency": "EUR" } """)
        )

        response.andExpect(status().isAccepted)
    }

    @Test
    fun `should close an account`() {
        val accountId = UUID.randomUUID()
        every { closeAccount.invoke(any()) } returns Unit

        val response = mvc.perform(delete("/accounts/$accountId"))

        response.andExpect(status().isAccepted)
    }

    @Test
    fun `should get the current balance of an account`() {
        val accountId = UUID.randomUUID()
        every { accountProjectionRepository.find(any()) } returns TestBuilders.Account.build(balance = 10.15.toBigDecimal())

        val response = mvc.perform(get("/accounts/$accountId/balance"))

        response.andExpect(status().isOk)
            .andExpect(content().json("""{ "accountId": "$accountId", "currentBalance": 10.15 }"""))
    }
}