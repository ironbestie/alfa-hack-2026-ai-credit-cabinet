package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class ScoringResult(
    val score: Int,
    val details: String
)

@Service
class ScoringService {

    fun calculateScore(application: Application): ScoringResult {
        var score = 50
        val reasons = mutableListOf<String>()

        val company = application.company

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

        val revenue = company.monthlyRevenue ?: 0.toBigDecimal()
        if (revenue > 1000000.toBigDecimal()) {
            score += 15
            reasons.add("Высокая выручка (+15)")
        } else if (revenue < 100000.toBigDecimal()) {
            score -= 15
            reasons.add("Низкая выручка (-15)")
        }

        val requestedAmount = application.requestedAmount
        val product = application.product

        if (requestedAmount > product.maxAmount.multiply(0.8.toBigDecimal())) {
            score -= 10
            reasons.add("Запрошена сумма близкая к лимиту (-10)")
        }

        val finalScore = score.coerceIn(0, 100)

        return ScoringResult(
            score = finalScore,
            details = reasons.joinToString(", ").ifEmpty { "Стандартный риск" }
        )
    }
}