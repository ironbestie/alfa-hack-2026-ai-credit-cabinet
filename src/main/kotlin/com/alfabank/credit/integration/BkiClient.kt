package com.alfabank.credit.integration

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(MockBkiClient::class.java)

    @CircuitBreaker(name = "bkiService", fallbackMethod = "fallbackBki")
    override fun fetchCreditHistory(inn: String): BkiData {
        if (inn.endsWith("00")) {
            log.warn("Имитация сбоя БКИ для ИНН: $inn")
            throw RuntimeException("BKI service is unavailable")
        }

        Thread.sleep(1000)

        return if (inn.endsWith("99")) {
            BkiData(inn, 2, 0, 0, "A")
        } else {
            BkiData(inn, 5, 3, 2, "C")
        }
    }

    fun fallbackBki(inn: String, ex: Exception): BkiData {
        log.error("Fallback для БКИ сработал. Причина: ${ex.message}")
        return BkiData(inn, 0, 0, 0, "UNKNOWN")
    }
}