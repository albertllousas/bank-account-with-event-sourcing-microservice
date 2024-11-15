package account.app.domain

sealed class AccountStatus {
    data object Initiated : AccountStatus()
    data object Opened : AccountStatus()
    data object Closed : AccountStatus()
}

enum class AccountType { MAIN }

enum class TransactionSource { SEPA_TRANSFER, CRYPTO_TX, CARD_TX }
