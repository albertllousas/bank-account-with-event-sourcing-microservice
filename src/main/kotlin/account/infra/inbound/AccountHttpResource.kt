package account.infra.inbound

import account.app.usecases.CloseAccountUseCase
import account.app.usecases.InitiateAccountUseCase
import account.infra.outbound.PostgresAccountProjectionRepository
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/accounts")
class AccountHttpResource(
    private val initiateAccount: InitiateAccountUseCase,
    private val closeAccount: CloseAccountUseCase,
    private val accountProjectionRepository: PostgresAccountProjectionRepository,
) {

    @PostMapping
    fun create(@RequestBody request: CreateAccountHttpRequest): ResponseEntity<Unit> =
        initiateAccount(request.accountId, request.accountType, request.currency, request.customerId)
            .let { ResponseEntity.status(ACCEPTED).build() }

    @DeleteMapping("/{accountId}")
    fun create(@PathVariable accountId: UUID ): ResponseEntity<Unit> =
        closeAccount(accountId)
            .let { ResponseEntity.status(ACCEPTED).build() }

    @GetMapping("/{accountId}/balance")
    fun balance(@PathVariable accountId: UUID ): ResponseEntity<AccountBalanceHttpResponse> =
        accountProjectionRepository.find(accountId)
            .let { ResponseEntity.ok(AccountBalanceHttpResponse(accountId, it.balance)) }
}

data class CreateAccountHttpRequest(val accountId: UUID, val customerId: UUID, val accountType: String, val currency: String)

data class AccountBalanceHttpResponse(val accountId: UUID, val currentBalance: BigDecimal)
