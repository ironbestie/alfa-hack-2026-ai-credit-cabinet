package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.integration.MockIntegrationClient
import com.alfabank.credit.repository.ConsentRepository
import com.alfabank.credit.scoring.ScoringInput
import com.alfabank.credit.scoring.TeamScoringResult
import com.alfabank.credit.scoring.TeamScoringService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class EnrichmentPayload(
    val input: ScoringInput,
    val sourcesOk: Boolean
)

data class ScoringPayload(
    val input: ScoringInput,
    val result: TeamScoringResult,
    val sourcesOk: Boolean = true
)

@Service
class ScoringService(
    private val mockClient: MockIntegrationClient,
    private val teamScoringService: TeamScoringService,
    private val consentRepository: ConsentRepository
) {
    /**
     * Этап ENRICHMENT: собираем данные из ЕГРЮЛ, ФНС, БКИ и банка.
     * БКИ вызывается только при наличии согласия — без него заявка уйдёт на ручную проверку.
     */
    fun enrich(application: Application): EnrichmentPayload {
        val company = application.company
        val egrul = mockClient.getEgrul(company.inn)
        val fns = mockClient.getFns(company.inn)
        val bank = mockClient.getBank(company.inn)

        val consents = application.id
            ?.let { consentRepository.findByApplicationId(it) }
            .orEmpty()
        val bkiConsent = consents.firstOrNull { it.consentType == "BKI" }
        val bki = bkiConsent?.let { mockClient.getBki(company.inn, it.id.toString()) }

        val registrationDate = egrul?.registrationDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: company.registrationDate
        val ageMonths = ChronoUnit.MONTHS.between(registrationDate, LocalDate.now()).toInt()

        val input = ScoringInput(
            businessAgeMonths = ageMonths,
            annualRevenue = fns?.revenue ?: company.annualRevenue,
            totalDebt = bki?.totalDebt ?: company.totalDebt,
            taxDebt = fns?.taxDebt ?: company.taxDebt,
            okved = egrul?.okved?.code ?: company.okved,
            bankRelationshipMonths = bank?.relationshipMonths ?: company.bankRelationshipMonths,
            maxOverdueDays = bki?.maxOverdueDays24m ?: 0,
            activeLoansCount = bki?.activeLoansCount ?: 0,
            requestedAmount = application.requestedAmount,
            termMonths = application.requestedTermMonths,
            hasCollateral = application.hasCollateral,
            companyStatus = egrul?.status ?: "ACTIVE",
            accountsSuspended = fns?.accountsSuspended ?: false
        )

        val sourcesOk = egrul != null && fns != null && bki != null
        return EnrichmentPayload(input, sourcesOk)
    }

    fun score(input: ScoringInput): TeamScoringResult = teamScoringService.score(input)

    fun stopFactor(input: ScoringInput): String? = teamScoringService.stopFactor(input)

    /** Оставлено для сценариев, где нужен полный расчёт одним вызовом. */
    fun calculateScore(application: Application): ScoringPayload {
        val enrichment = enrich(application)
        return ScoringPayload(enrichment.input, score(enrichment.input), enrichment.sourcesOk)
    }
}
