package com.alfabank.credit.scoring

import com.alfabank.credit.config.PolicyProperties
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
    val contribution: Int = 0 // <-- ДОБАВЛЕНО
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
    private val policy: PolicyProperties,
    private val gradeResolver: GradeResolver
) {
    companion object {
        private val HIGH_RISK_OKVED = listOf("56.", "41.", "42.", "43.")
        private val MANUAL_REVIEW_OVER = BigDecimal("10000000")
        private val GUARANTEE_RATE = BigDecimal("19.5")
        private val COLLATERAL_DISCOUNT = BigDecimal("2.0")
        private val STEP = BigDecimal("100000")
        private val MC = MathContext.DECIMAL64
    }

    fun score(input: ScoringInput): TeamScoringResult {
        val f = mutableListOf<ScoreFactor>()
        val revenue = input.annualRevenue
        val revenueMln = revenue.divide(BigDecimal("1000000"), 1, RoundingMode.HALF_UP)

        // 1. Возраст бизнеса
        when {
            input.businessAgeMonths >= 24 -> f += ScoreFactor("Возраст бизнеса больше 2 лет", 25, "${input.businessAgeMonths} мес.")
            input.businessAgeMonths >= 12 -> f += ScoreFactor("Возраст бизнеса 1–2 года", 10, "${input.businessAgeMonths} мес.")
            else -> f += ScoreFactor("Бизнесу меньше года", -50, "${input.businessAgeMonths} мес.", "R03")
        }
        // 2. Выручка
        when {
            revenue >= BigDecimal("10000000") -> f += ScoreFactor("Выручка больше 10 млн", 30, "$revenueMln млн ₽")
            revenue >= BigDecimal("5000000") -> f += ScoreFactor("Выручка 5–10 млн", 15, "$revenueMln млн ₽")
            revenue >= BigDecimal("1000000") -> f += ScoreFactor("Выручка 1–5 млн", 0, "$revenueMln млн ₽")
            else -> f += ScoreFactor("Выручка меньше 1 млн", -20, "$revenueMln млн ₽")
        }
        // 3. Долговая нагрузка: общий долг к годовой выручке
        val dti = input.totalDebt.divide(revenue.max(BigDecimal.ONE), MC)
        val dtiPct = "${dti.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)}%"
        when {
            dti < BigDecimal("0.3") -> f += ScoreFactor("Низкая долговая нагрузка", 20, dtiPct)
            dti < BigDecimal("0.6") -> f += ScoreFactor("Умеренная долговая нагрузка", 0, dtiPct)
            else -> f += ScoreFactor("Высокая долговая нагрузка", -40, dtiPct, "R01")
        }
        // 4. Просрочки в БКИ
        when {
            input.maxOverdueDays == 0 -> f += ScoreFactor("Нет просрочек", 10, "0 дней")
            input.maxOverdueDays <= 30 -> f += ScoreFactor("Незначительные просрочки", -20, "${input.maxOverdueDays} дн.", "R02")
            else -> f += ScoreFactor("Существенные просрочки", -50, "${input.maxOverdueDays} дн.", "R02")
        }
        // 5. Количество действующих кредитов
        if (input.activeLoansCount > 0) {
            f += ScoreFactor("Действующие кредиты", -10 * input.activeLoansCount, "${input.activeLoansCount} шт.", "R05")
        }
        // 6. Налоговая задолженность
        if (input.taxDebt.signum() == 0) f += ScoreFactor("Нет налоговой задолженности", 10, "0 ₽")
        else f += ScoreFactor("Есть налоговая задолженность", -30, "${input.taxDebt.toPlainString()} ₽", "R04")
        // 7. Отраслевой риск
        if (HIGH_RISK_OKVED.any { input.okved.startsWith(it) }) f += ScoreFactor("Высокорисковая отрасль", -15, input.okved)
        // 8. Сумма кредита к выручке
        val loanToRevenue = input.requestedAmount.divide(revenue.max(BigDecimal.ONE), MC)
        if (loanToRevenue > BigDecimal("0.5")) {
            f += ScoreFactor("Сумма кредита больше 50% выручки", -20,
                "${loanToRevenue.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)}%", "R06")
        }
        // 9. Залог
        if (input.hasCollateral) f += ScoreFactor("Есть залог", 20, "да")
        // 10. Клиент банка больше года
        if (input.bankRelationshipMonths >= 12) f += ScoreFactor("Клиент банка больше года", 15, "${input.bankRelationshipMonths} мес.")

        // ИСПОЛЬЗУЕМ YAML ДЛЯ БАЗЫ И МНОЖИТЕЛЯ
        val rawScore = scorecard.base.baseValue + scorecard.base.multiplier * f.sumOf { it.points }
        val score = rawScore.coerceIn(0, 1000)

        // ИСПОЛЬЗУЕМ YAML ДЛЯ ГРЕЙДОВ (единый resolver с экраном решения)
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

    fun decide(input: ScoringInput, productRatePercent: BigDecimal, sourcesOk: Boolean = true): DecisionOutcome {
        stopFactor(input)?.let { return DecisionOutcome("REJECTED", it) }
        if (!sourcesOk || input.requestedAmount > MANUAL_REVIEW_OVER) {
            return DecisionOutcome("MANUAL_REVIEW", "Нужна ручная проверка специалистом")
        }
        val result = score(input)
        if (result.grade == "D" || result.grade == "E") return DecisionOutcome("REJECTED", "Грейд ${result.grade}, балл ${result.score}", result)

        // ИСПОЛЬЗУЕМ YAML ДЛЯ ПОЛИТИКИ
        val gradePolicy = policy.grades[result.grade] ?: return DecisionOutcome("REJECTED", "Политика для грейда не найдена", result)

        val rate = productRatePercent.add(BigDecimal(gradePolicy.rateSurcharge))
        val limit = roundDown(input.annualRevenue.multiply(BigDecimal(gradePolicy.maxAmountPctRevenue).divide(BigDecimal(100))))

        val amount = input.requestedAmount
        if (amount <= limit) {
            return DecisionOutcome("APPROVED", "Грейд ${result.grade}, сумма в пределах лимита", result,
                listOf(offer("REQUESTED", amount, input.termMonths, rate)))
        }
        val middle = roundDown(limit.add(amount).divide(BigDecimal(2), MC))
        val offers = listOf(
            offer("LIMIT", limit, input.termMonths, rate),
            offer("GUARANTEE", amount, maxOf(input.termMonths, 36), GUARANTEE_RATE),
            offer("COLLATERAL", middle, input.termMonths + 6, productRatePercent.subtract(COLLATERAL_DISCOUNT)),
        )
        return DecisionOutcome("COUNTER_OFFER", "Сумма больше лимита грейда ${result.grade}: ${limit.toPlainString()} ₽", result, offers)
    }

    private fun roundDown(x: BigDecimal): BigDecimal = x.divide(STEP, 0, RoundingMode.DOWN).multiply(STEP)

    private fun offer(kind: String, amount: BigDecimal, term: Int, ratePercent: BigDecimal): OfferOption {
        val r = ratePercent.divide(BigDecimal(1200), MC)
        val factor = BigDecimal.ONE.add(r).pow(term, MC)
        val payment = amount.multiply(r).multiply(factor).divide(factor.subtract(BigDecimal.ONE), MC)
        val overpay = payment.multiply(BigDecimal(term)).subtract(amount)
        return OfferOption(kind, amount, term, ratePercent, round100(payment), round100(overpay))
    }

    private fun round100(x: BigDecimal): BigDecimal = x.divide(BigDecimal(100), 0, RoundingMode.HALF_UP).multiply(BigDecimal(100))
}