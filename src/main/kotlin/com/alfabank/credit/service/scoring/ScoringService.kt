package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.integration.BkiClient
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ScoringFactor(val name: String, val impact: Int, val description: String)
data class ScoringResult(val score: Int, val grade: String, val details: String, val factors: List<ScoringFactor>)

@Service
class ScoringService(
    private val bkiClient: BkiClient
) {
    fun calculateScore(application: Application): ScoringResult {
        var score = 530
        val reasons = mutableListOf<String>()

        val company = application.company

        val yearsInBusiness = ChronoUnit.YEARS.between(company.registrationDate, LocalDate.now())
        if (yearsInBusiness >= 5) {
            score += 25
            reasons.add("Возраст бизнеса > 5 лет (+25)")
        } else if (yearsInBusiness in 3..4) {
            score += 13
            reasons.add("Возраст бизнеса 3-5 лет (+13)")
        } else if (yearsInBusiness < 1) {
            score -= 40
            reasons.add("Бизнес моложе 1 года (-40)")
        }

        val revenue = company.annualRevenue
        if (revenue > 10000000.toBigDecimal()) {
            score += 15
            reasons.add("Высокая выручка (+15)")
        } else if (revenue < 1000000.toBigDecimal()) {
            score -= 15
            reasons.add("Низкая выручка (-15)")
        }

        val dti = company.totalDebt.toDouble() / revenue.toDouble()
        if (dti > 0.7) {
            score -= 63
            reasons.add("Высокая долговая нагрузка (>70%) (-63)")
        } else if (dti < 0.3) {
            score += 21
            reasons.add("Низкая долговая нагрузка (<30%) (+21)")
        }

        val bkiData = bkiClient.fetchCreditHistory(company.inn)
        if (bkiData.overdueCount > 0) {
            val penalty = bkiData.overdueCount * 15
            score -= penalty
            reasons.add("Просрочки в БКИ: ${bkiData.overdueCount} (-$penalty)")
        } else {
            score += 40
            reasons.add("Идеальная кредитная история (+40)")
        }

        val finalScore = score.coerceIn(0, 1000)

        val grade = when {
            finalScore >= 660 -> "A"
            finalScore >= 620 -> "B"
            finalScore >= 580 -> "C"
            finalScore >= 540 -> "D"
            else -> "E"
        }

        return ScoringResult(
            score = finalScore,
            grade = grade,
            details = reasons.joinToString(", ").ifEmpty { "Стандартный риск" },
            factors = emptyList()
        )
    }
}