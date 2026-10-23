package com.alfabank.credit.scoring

import com.alfabank.credit.config.BaseScore
import com.alfabank.credit.config.GradePolicy
import com.alfabank.credit.config.PolicyProperties
import com.alfabank.credit.config.ScorecardProperties
import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.Company
import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.service.scoring.DecisionService
import com.alfabank.credit.service.scoring.ScoringPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class TeamScoringTest {

    private val scorecard = ScorecardProperties().apply {
        base = BaseScore().apply {
            baseValue = 600
            multiplier = 2
        }
        grades = mapOf("A" to 700, "B" to 600, "C" to 500, "D" to 0)
    }

    private val gradeResolver = GradeResolver(scorecard)

    private val policy = PolicyProperties().apply {
        manualReviewOver = BigDecimal("10000000")
        grades = mapOf(
            "A" to GradePolicy().apply {
                rateSurcharge = 0.0
                maxAmountPctRevenue = 40
            },
            "B" to GradePolicy().apply {
                rateSurcharge = 0.5
                maxAmountPctRevenue = 30
            },
            "C" to GradePolicy().apply {
                rateSurcharge = 1.0
                maxAmountPctRevenue = 20
            },
            "D" to GradePolicy().apply {
                rateSurcharge = 4.5
                maxAmountPctRevenue = 10
            }
        )
    }

    private val teamScoring = TeamScoringService(scorecard, gradeResolver)
    private val decisionService = DecisionService(teamScoring, policy)

    @Test
    fun `ИНН 7704983159 получает грейд A и одобрение`() {
        val input = inputA()
        val result = teamScoring.score(input)

        assertEquals(800, result.score)
        assertEquals("A", result.grade)

        val decision = decisionService.makeDecision(application(input), ScoringPayload(input, result))
        assertEquals("APPROVED", decision.status)
        assertEquals(1, decision.offers.size)
    }

    @Test
    fun `ИНН 7703168426 получает грейд C и контрпредложение 3 млн`() {
        val input = inputC()
        val result = teamScoring.score(input)

        assertEquals(580, result.score)
        assertEquals("C", result.grade)

        val decision = decisionService.makeDecision(application(input), ScoringPayload(input, result))
        assertEquals("COUNTER_OFFER", decision.status)
        assertEquals(3, decision.offers.size)

        val limitOffer = decision.offers.first()
        assertEquals("LIMIT", limitOffer.kind)
        assertEquals(BigDecimal("3000000"), limitOffer.amount)
        assertEquals(BigDecimal("157100"), limitOffer.monthlyPayment)
        assertEquals(BigDecimal("770900"), limitOffer.overpayment)
    }

    @Test
    fun `ИНН 7729501760 получает грейд D и отказ`() {
        val input = inputD()
        val result = teamScoring.score(input)

        assertEquals(200, result.score)
        assertEquals("D", result.grade)

        val decision = decisionService.makeDecision(application(input), ScoringPayload(input, result))
        assertEquals("REJECTED", decision.status)
        assertTrue(decision.reason.contains("Грейд D"))
    }

    @Test
    fun `ровно 60 процентов долговой нагрузки не даёт штраф`() {
        val input = inputA().copy(
            annualRevenue = BigDecimal("10000000"),
            totalDebt = BigDecimal("6000000")
        )
        val result = teamScoring.score(input)
        val debtFactor = result.factors.first { it.name.contains("долговая нагрузка") }

        assertEquals(0, debtFactor.points)
        assertEquals(null, debtFactor.reasonCode)
    }

    @Test
    fun `недоступный источник уводит заявку на ручную проверку`() {
        val input = inputA()
        val result = teamScoring.score(input)

        val decision = decisionService.makeDecision(
            application(input),
            ScoringPayload(input, result, sourcesOk = false)
        )
        assertEquals("MANUAL_REVIEW", decision.status)
    }

    @Test
    fun `ликвидированная компания получает стоп-фактор`() {
        val input = inputA().copy(companyStatus = "LIQUIDATED")
        val result = teamScoring.score(input)

        val decision = decisionService.makeDecision(application(input), ScoringPayload(input, result))
        assertEquals("REJECTED", decision.status)
        assertTrue(decision.reason.contains("ликвидирована"))
    }

    @Test
    fun `примеры расчёта платежа совпадают с кейсом`() {
        assertEquals(BigDecimal("157100"), OfferMath.monthlyPayment(BigDecimal("3000000"), 24, 23.0))
        assertEquals(BigDecimal("770900"), OfferMath.overpayment(BigDecimal("3000000"), 24, 23.0))
        assertEquals(BigDecimal("184500"), OfferMath.monthlyPayment(BigDecimal("5000000"), 36, 19.5))
        assertEquals(BigDecimal("1643700"), OfferMath.overpayment(BigDecimal("5000000"), 36, 19.5))
        assertEquals(BigDecimal("170500"), OfferMath.monthlyPayment(BigDecimal("4000000"), 30, 20.0))
        assertEquals(BigDecimal("1115600"), OfferMath.overpayment(BigDecimal("4000000"), 30, 20.0))
    }

    private fun inputA() = ScoringInput(
        businessAgeMonths = 100,
        annualRevenue = BigDecimal("50000000"),
        totalDebt = BigDecimal("5000000"),
        taxDebt = BigDecimal.ZERO,
        okved = "62.01",
        bankRelationshipMonths = 36,
        maxOverdueDays = 0,
        activeLoansCount = 1,
        requestedAmount = BigDecimal("3000000"),
        termMonths = 24,
        hasCollateral = false
    )

    private fun inputC() = ScoringInput(
        businessAgeMonths = 55,
        annualRevenue = BigDecimal("15000000"),
        totalDebt = BigDecimal("9300000"),
        taxDebt = BigDecimal.ZERO,
        okved = "10.71",
        bankRelationshipMonths = 24,
        maxOverdueDays = 3,
        activeLoansCount = 3,
        requestedAmount = BigDecimal("5000000"),
        termMonths = 24,
        hasCollateral = false
    )

    private fun inputD() = ScoringInput(
        businessAgeMonths = 11,
        annualRevenue = BigDecimal("5000000"),
        totalDebt = BigDecimal("4000000"),
        taxDebt = BigDecimal("150000"),
        okved = "41.20",
        bankRelationshipMonths = 8,
        maxOverdueDays = 45,
        activeLoansCount = 1,
        requestedAmount = BigDecimal("5000000"),
        termMonths = 24,
        hasCollateral = false
    )

    private fun application(input: ScoringInput): Application {
        val company = Company(
            inn = "7700000000",
            ogrn = "1000000000000",
            name = "Тестовая компания",
            okved = input.okved,
            okvedDescription = "Тест",
            directorName = "Иванов Иван Иванович",
            registrationDate = LocalDate.now().minusMonths(input.businessAgeMonths.toLong()),
            category = "Малое",
            status = "active",
            annualRevenue = input.annualRevenue,
            totalDebt = input.totalDebt,
            taxDebt = input.taxDebt,
            bankRelationshipMonths = input.bankRelationshipMonths
        )
        val product = CreditProduct(
            name = "Оборотный кредит",
            description = "Тест",
            minAmount = BigDecimal("100000"),
            maxAmount = BigDecimal("30000000"),
            minTerm = 3,
            maxTerm = 36,
            baseRate = 22.0,
            tags = "[]"
        )
        return Application(
            company = company,
            product = product,
            requestedAmount = input.requestedAmount,
            requestedTermMonths = input.termMonths,
            hasCollateral = input.hasCollateral
        )
    }
}
