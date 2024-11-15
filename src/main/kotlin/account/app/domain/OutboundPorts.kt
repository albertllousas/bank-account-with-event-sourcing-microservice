package account.app.domain

import java.util.UUID

object OutboundPorts {

    interface AccountWriteRepository {
        fun save(account: Account)
    }

    interface ErrorEventReporter {
        fun report(event: AccountError)
    }

    interface AccountReadRepository {
        fun find(accountId: UUID): Account
    }
}
