package com.alfabank.credit.integration

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal

data class EgrulResponse(val inn: String, val name: String, val status: String, val registrationDate: String, val okved: String)
data class FnsResponse(val revenue: BigDecimal, val taxDebt: BigDecimal, val accountsSuspended: Boolean)
data class BkiResponse(val totalDebt: BigDecimal, val maxOverdueDays24m: Int, val activeLoansCount: Int)

@Service
class MockIntegrationClient(@Value("\${app.mocks.url}") baseUrl: String) {
    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2000)
            setReadTimeout(3000)
        })
        .build()

    @Retry(name = "mocksRetry")
    @CircuitBreaker(name = "mocksCircuit", fallbackMethod = "egrulFallback")
    fun getEgrul(inn: String): EgrulResponse? {
        return restClient.get().uri("/egrul/v1/companies/$inn").retrieve().body(EgrulResponse::class.java)
    }

    @Retry(name = "mocksRetry")
    @CircuitBreaker(name = "mocksCircuit", fallbackMethod = "fnsFallback")
    fun getFns(inn: String): FnsResponse? {
        return restClient.get().uri("/fns/v1/companies/$inn/finance").retrieve().body(FnsResponse::class.java)
    }

    @Retry(name = "mocksRetry")
    @CircuitBreaker(name = "mocksCircuit", fallbackMethod = "bkiFallback")
    fun getBki(inn: String, consentId: String = "demo"): BkiResponse? {
        return restClient.post().uri("/bki/v1/reports")
            .body(mapOf("inn" to inn, "consentId" to consentId))
            .retrieve().body(BkiResponse::class.java)
    }

    // Fallback-методы (если мок упал)
    fun egrulFallback(inn: String, ex: Exception): EgrulResponse? = null
    fun fnsFallback(inn: String, ex: Exception): FnsResponse? = null
    fun bkiFallback(inn: String, consentId: String, ex: Exception): BkiResponse? = null
}