package com.alfabank.credit.service.scoring

import com.alfabank.credit.config.PolicyProperties
import com.alfabank.credit.domain.Application
import com.alfabank.credit.scoring.DecisionOutcome
import com.alfabank.credit.scoring.OfferOption
import com.alfabank.credit.scoring.TeamScoringService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale

@Service
class DecisionService(
    private val teamScoringService: TeamScoringService,
    private val policy: PolicyProperties
) {
    companion object {
        private val GUARANTEE_RATE = BigDecimal("19.5")
        private val COLLATERAL_DISCOUNT = BigDecimal("2.0")
        private val STEP = BigDecimal("100000")
        private val MC = MathContext.DECIMAL64
    }

    fun makeDecision(application: Application, payload: ScoringPayload): DecisionOutcome {
        val input = payload.input

        teamScoringService.stopFactor(input)?.let {
            return DecisionOutcome("REJECTED", it)
        }

        if (!payload.sourcesOk) {
            return DecisionOutcome(
                "MANUAL_REVIEW",
                "Не все источники данных ответили. Заявку проверит специалист."
            )
        }

        if (input.requestedAmount > policy.manualReviewOver) {
            return DecisionOutcome(
                "MANUAL_REVIEW",
                "Сумма заявки больше ${"%,d".format(Locale.US, policy.manualReviewOver.toBigInteger()).replace(',', ' ')} ₽. Нужна ручная проверка."
            )
        }

        val result = payload.result
        if (result.grade == "D" || result.grade == "E") {
            return DecisionOutcome("REJECTED", "Грейд ${result.grade}, балл ${result.score}")
        }

        val gradePolicy = policy.grades[result.grade]
            ?: return DecisionOutcome("REJECTED", "Политика для грейда не найдена")

        val productRate = BigDecimal.valueOf(application.product.baseRate)
        val rate = productRate.add(BigDecimal.valueOf(gradePolicy.rateSurcharge))
        val limit = roundDown(
            input.annualRevenue.multiply(BigDecimal(gradePolicy.maxAmountPctRevenue))
                .divide(BigDecimal(100), MC)
        )

        if (input.requestedAmount <= limit) {
            return DecisionOutcome(
                "APPROVED",
                "Грейд ${result.grade}, сумма в пределах лимита",
                listOf(offer("REQUESTED", input.requestedAmount, input.termMonths, rate))
            )
        }

        val middle = roundDown(limit.add(input.requestedAmount).divide(BigDecimal(2), MC))
        val offers = listOf(
            offer("LIMIT", limit, input.termMonths, rate),
            offer("GUARANTEE", input.requestedAmount, maxOf(input.termMonths, 36), GUARANTEE_RATE),
            offer(
                "COLLATERAL",
                middle,
                input.termMonths + 6,
                productRate.subtract(COLLATERAL_DISCOUNT)
            )
        )

        return DecisionOutcome(
            "COUNTER_OFFER",
            "Сумма больше лимита грейда ${result.grade}: ${limit.toPlainString()} ₽",
            offers
        )
    }

    private fun roundDown(value: BigDecimal): BigDecimal =
        value.divide(STEP, 0, RoundingMode.DOWN).multiply(STEP)

    private fun offer(kind: String, amount: BigDecimal, termMonths: Int, ratePercent: BigDecimal): OfferOption =
        OfferOption(
            kind = kind,
            amount = amount,
            termMonths = termMonths,
            ratePercent = ratePercent
        )
}
