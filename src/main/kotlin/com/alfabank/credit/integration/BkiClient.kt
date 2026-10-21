package com.alfabank.credit.integration

import org.springframework.stereotype.Service

data class BkiData(
    val inn: String,
    val totalLoans: Int,
    val activeLoans: Int,
    val overdueCount: Int,
    val rating: String
)

interface BkiClient {
    fun fetchCreditHistory(inn: String): BkiData
}

@Service
class MockBkiClient : BkiClient {
    override fun fetchCreditHistory(inn: String): BkiData {
        Thread.sleep(1000) // Имитация задержки сети

        // Логика для демо: если ИНН заканчивается на "99", история идеальная. Иначе - есть просрочки.
        return if (inn.endsWith("99")) {
            BkiData(inn, 2, 0, 0, "A")
        } else {
            BkiData(inn, 5, 3, 2, "C")
        }
    }
}