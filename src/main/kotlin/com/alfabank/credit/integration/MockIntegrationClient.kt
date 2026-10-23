package com.alfabank.credit.integration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class OkvedResponse(
    val code: String = "",
    val name: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DirectorResponse(
    val fullName: String? = null,
    val position: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EgrulResponse(
    val inn: String = "",
    val ogrn: String? = null,
    val shortName: String? = null,
    val fullName: String? = null,
    val status: String = "ACTIVE",
    val registrationDate: String? = null,
    val okved: OkvedResponse? = null,
    val director: DirectorResponse? = null
) {
    val displayName: String
        get() = shortName ?: fullName ?: inn
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class FnsResponse(
    val revenue: BigDecimal = BigDecimal.ZERO,
    val taxDebt: BigDecimal = BigDecimal.ZERO,
    val accountsSuspended: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BkiActiveLoan(
    val lender: String? = null,
    val type: String? = null,
    val balance: BigDecimal? = null,
    val monthlyPayment: BigDecimal? = null,
    val openedAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BkiResponse(
    val inn: String? = null,
    val totalDebt: BigDecimal = BigDecimal.ZERO,
    val maxOverdueDays24m: Int = 0,
    val activeLoans: List<BkiActiveLoan> = emptyList()
) {
    val activeLoansCount: Int
        get() = activeLoans.size
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class BankResponse(
    val inn: String? = null,
    val isClient: Boolean = false,
    val clientSince: String? = null,
    val relationshipMonths: Int = 0,
    val avgMonthlyInflow: BigDecimal? = null,
    val monthlyInflows: List<BigDecimal> = emptyList()
)

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

    @CircuitBreaker(name = "mocksCircuit", fallbackMethod = "bkiFallback")
    fun getBki(inn: String, consentId: String): BkiResponse? {
        return restClient.post().uri("/bki/v1/reports")
            .body(mapOf("inn" to inn, "consentId" to consentId))
            .retrieve().body(BkiResponse::class.java)
    }

    @Retry(name = "mocksRetry")
    @CircuitBreaker(name = "mocksCircuit", fallbackMethod = "bankFallback")
    fun getBank(inn: String): BankResponse? {
        return restClient.get().uri("/bank/v1/clients/$inn").retrieve().body(BankResponse::class.java)
    }

    fun egrulFallback(inn: String, ex: Exception): EgrulResponse? = null
    fun fnsFallback(inn: String, ex: Exception): FnsResponse? = null
    fun bkiFallback(inn: String, consentId: String, ex: Exception): BkiResponse? = null
    fun bankFallback(inn: String, ex: Exception): BankResponse? = null
}
