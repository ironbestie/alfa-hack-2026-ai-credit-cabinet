package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.integration.BkiClient
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ScoringResult(
    val score: Int,
    val details: String
)

@Service
class ScoringService(
    private val bkiClient: BkiClient
) {

    fun calculateScore(application: Application): ScoringResult {
        var score = 50
        val reasons = mutableListOf<String>()

        val company = application.company

        // 1. Возраст бизнеса
        val yearsInBusiness = company.registrationDate?.let {
            ChronoUnit.YEARS.between(it, LocalDate.now())
        } ?: 0

        if (yearsInBusiness >= 3) {
            score += 20
            reasons.add("Возраст бизнеса более 3 лет (+20)")
        } else if (yearsInBusiness < 1) {
            score -= 20
            reasons.add("Бизнес моложе 1 года (-20)")
        }

        // 2. Выручка
        val revenue = company.monthlyRevenue ?: 0.toBigDecimal()
        if (revenue > 1000000.toBigDecimal()) {
            score += 15
            reasons.add("Высокая выручка (+15)")
        } else if (revenue < 100000.toBigDecimal()) {
            score -= 15
            reasons.add("Низкая выручка (-15)")
        }

        // 3. Сумма к лимиту
        val requestedAmount = application.requestedAmount
        val product = application.product

        if (requestedAmount > product.maxAmount.multiply(0.8.toBigDecimal())) {
            score -= 10
            reasons.add("Запрошена сумма близкая к лимиту (-10)")
        }

        // 4. Данные из БКИ (Кредитная история)
        val bkiData = bkiClient.fetchCreditHistory(company.inn)
        if (bkiData.overdueCount > 0) {
            val penalty = bkiData.overdueCount * 15
            score -= penalty
            reasons.add("Найдено ${bkiData.overdueCount} просрочек (-$penalty)")
        } else {
            reasons.add("Идеальная кредитная история (0 просрочек)")
        }

        val finalScore = score.coerceIn(0, 100)

        return ScoringResult(
            score = finalScore,
            details = reasons.joinToString(", ").ifEmpty { "Стандартный риск" }
        )
    }
}