package com.alfabank.credit.scoring

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object OfferMath {
    private val MC = MathContext.DECIMAL64
    private val HUNDRED = BigDecimal("100")

    fun monthlyPayment(amount: BigDecimal, termMonths: Int, ratePercent: Double): BigDecimal =
        round100(exactMonthlyPayment(amount, termMonths, BigDecimal.valueOf(ratePercent)))

    fun overpayment(amount: BigDecimal, termMonths: Int, ratePercent: Double): BigDecimal {
        val payment = exactMonthlyPayment(amount, termMonths, BigDecimal.valueOf(ratePercent))
        return round100(payment.multiply(BigDecimal(termMonths)).subtract(amount))
    }

    fun exactMonthlyPayment(amount: BigDecimal, termMonths: Int, ratePercent: BigDecimal): BigDecimal {
        val monthlyRate = ratePercent.divide(BigDecimal(1200), MC)
        val factor = BigDecimal.ONE.add(monthlyRate).pow(termMonths, MC)
        return amount.multiply(monthlyRate).multiply(factor)
            .divide(factor.subtract(BigDecimal.ONE), MC)
    }

    fun round100(value: BigDecimal): BigDecimal =
        value.divide(HUNDRED, 0, RoundingMode.HALF_UP).multiply(HUNDRED)
}
