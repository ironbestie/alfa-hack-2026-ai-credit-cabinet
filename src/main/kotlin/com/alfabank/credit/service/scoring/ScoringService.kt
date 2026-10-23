package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.integration.MockIntegrationClient
import com.alfabank.credit.scoring.ScoringInput
import com.alfabank.credit.scoring.TeamScoringResult
import com.alfabank.credit.scoring.TeamScoringService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ScoringPayload(
    val input: ScoringInput,
    val result: TeamScoringResult,
    val sourcesOk: Boolean = true
)

@Service
class ScoringService(
    private val mockClient: MockIntegrationClient,
    private val teamScoringService: TeamScoringService // <-- Внедрили
) {
    fun calculateScore(application: Application): ScoringPayload {
        val company = application.company
        val egrul = mockClient.getEgrul(company.inn)
        val fns = mockClient.getFns(company.inn)
        val bki = mockClient.getBki(company.inn)

        val ageMonths = ChronoUnit.MONTHS.between(company.registrationDate, LocalDate.now()).toInt()

        val input = ScoringInput(
            businessAgeMonths = ageMonths,
            annualRevenue = fns?.revenue ?: company.annualRevenue,
            totalDebt = bki?.totalDebt ?: company.totalDebt,
            taxDebt = fns?.taxDebt ?: company.taxDebt,
            okved = company.okved,
            bankRelationshipMonths = company.bankRelationshipMonths,
            maxOverdueDays = bki?.maxOverdueDays24m ?: 0,
            activeLoansCount = bki?.activeLoansCount ?: 0,
            requestedAmount = application.requestedAmount,
            termMonths = application.requestedTermMonths,
            hasCollateral = false,
            companyStatus = egrul?.status ?: "ACTIVE",
            accountsSuspended = fns?.accountsSuspended ?: false
        )

        val result = teamScoringService.score(input) // <-- Вызов
        val sourcesOk = egrul != null && fns != null && bki != null
        return ScoringPayload(input, result, sourcesOk)
    }
}