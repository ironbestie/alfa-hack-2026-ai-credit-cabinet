package com.alfabank.credit.scoring

import com.alfabank.credit.config.ScorecardProperties
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class ScoringInput(
    val businessAgeMonths: Int,
    val annualRevenue: BigDecimal,
    val totalDebt: BigDecimal,
    val taxDebt: BigDecimal,
    val okved: String,
    val bankRelationshipMonths: Int,
    val maxOverdueDays: Int,
    val activeLoansCount: Int,
    val requestedAmount: BigDecimal,
    val termMonths: Int,
    val hasCollateral: Boolean,
    val companyStatus: String = "ACTIVE",
    val accountsSuspended: Boolean = false,
)

data class ScoreFactor(
    val name: String,
    val points: Int,
    val description: String,
    val reasonCode: String? = null,
    val contribution: Int = 0
)

data class TeamScoringResult(
    val score: Int,
    val grade: String,
    val riskLevel: String,
    val factors: List<ScoreFactor>,
)

data class OfferOption(
    val kind: String,
    val amount: BigDecimal,
    val termMonths: Int,
    val ratePercent: BigDecimal,
    val monthlyPayment: BigDecimal,
    val overpayment: BigDecimal,
)

data class DecisionOutcome(
    val status: String,
    val reason: String,
    val scoring: TeamScoringResult? = null,
    val offers: List<OfferOption> = emptyList(),
)

@Component
class TeamScoringService(
    private val scorecard: ScorecardProperties,
    private val gradeResolver: GradeResolver
) {
    companion object {
        private val HIGH_RISK_OKVED = listOf("56.", "41.", "42.", "43.")
        private val MC = MathContext.DECIMAL64
    }

    fun score(input: ScoringInput): TeamScoringResult {
        val f = mutableListOf<ScoreFactor>()
        val revenue = input.annualRevenue
        val revenueMln = revenue.divide(BigDecimal("1000000"), 1, RoundingMode.HALF_UP)

        when {
            input.businessAgeMonths >= 24 -> f += ScoreFactor("Возраст бизнеса больше 2 лет", 25, "${input.businessAgeMonths} мес.")
            input.businessAgeMonths >= 12 -> f += ScoreFactor("Возраст бизнеса 1–2 года", 10, "${input.businessAgeMonths} мес.")
            else -> f += ScoreFactor("Бизнесу меньше года", -50, "${input.businessAgeMonths} мес.", "R03")
        }
        when {
            revenue >= BigDecimal("10000000") -> f += ScoreFactor("Выручка больше 10 млн", 30, "$revenueMln млн ₽")
            revenue >= BigDecimal("5000000") -> f += ScoreFactor("Выручка 5–10 млн", 15, "$revenueMln млн ₽")
            revenue >= BigDecimal("1000000") -> f += ScoreFactor("Выручка 1–5 млн", 0, "$revenueMln млн ₽")
            else -> f += ScoreFactor("Выручка меньше 1 млн", -20, "$revenueMln млн ₽")
        }
        val dti = input.totalDebt.divide(revenue.max(BigDecimal.ONE), MC)
        val dtiPct = "${dti.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)}%"
        when {
            dti < BigDecimal("0.3") -> f += ScoreFactor("Низкая долговая нагрузка", 20, dtiPct)
            dti <= BigDecimal("0.6") -> f += ScoreFactor("Умеренная долговая нагрузка", 0, dtiPct)
            else -> f += ScoreFactor("Высокая долговая нагрузка", -40, dtiPct, "R01")
        }
        when {
            input.maxOverdueDays == 0 -> f += ScoreFactor("Нет просрочек", 10, "0 дней")
            input.maxOverdueDays <= 30 -> f += ScoreFactor("Незначительные просрочки", -20, "${input.maxOverdueDays} дн.", "R02")
            else -> f += ScoreFactor("Существенные просрочки", -50, "${input.maxOverdueDays} дн.", "R02")
        }
        if (input.activeLoansCount > 0) {
            f += ScoreFactor("Действующие кредиты", -10 * input.activeLoansCount, "${input.activeLoansCount} шт.", "R05")
        }
        if (input.taxDebt.signum() == 0) f += ScoreFactor("Нет налоговой задолженности", 10, "0 ₽")
        else f += ScoreFactor("Есть налоговая задолженность", -30, "${input.taxDebt.toPlainString()} ₽", "R04")
        if (HIGH_RISK_OKVED.any { input.okved.startsWith(it) }) f += ScoreFactor("Высокорисковая отрасль", -15, input.okved)
        val loanToRevenue = input.requestedAmount.divide(revenue.max(BigDecimal.ONE), MC)
        if (loanToRevenue > BigDecimal("0.5")) {
            f += ScoreFactor("Сумма кредита больше 50% выручки", -20,
                "${loanToRevenue.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)}%", "R06")
        }
        if (input.hasCollateral) f += ScoreFactor("Есть залог", 20, "да")
        if (input.bankRelationshipMonths >= 12) f += ScoreFactor("Клиент банка больше года", 15, "${input.bankRelationshipMonths} мес.")

        val rawScore = scorecard.base.baseValue + scorecard.base.multiplier * f.sumOf { it.points }
        val score = rawScore.coerceIn(0, 1000)

        val grade = gradeResolver.gradeFor(score)

        val risk = gradeResolver.riskLevel(grade)

        val finalFactors = f.map { it.copy(contribution = it.points * scorecard.base.multiplier) }

        return TeamScoringResult(score, grade, risk, finalFactors)
    }

    fun stopFactor(input: ScoringInput): String? = when {
        input.companyStatus != "ACTIVE" -> "Компания ликвидирована или находится в процессе ликвидации"
        input.accountsSuspended -> "Операции по счетам компании приостановлены"
        input.maxOverdueDays >= 90 -> "В кредитной истории есть просрочка 90 дней и больше"
        else -> null
    }

}