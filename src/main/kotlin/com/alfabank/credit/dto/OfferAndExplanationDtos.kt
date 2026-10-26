package com.alfabank.credit.dto

import java.math.BigDecimal
import java.util.UUID

data class OfferResponse(
    val id: UUID,
    val amount: BigDecimal,
    val termMonths: Int,
    val interestRate: Double,
    val type: String,
    val monthlyPayment: BigDecimal,
    val overpayment: BigDecimal
)

data class ExplanationResponse(
    val summary: String,
    val reasons: List<String>,
    val nextSteps: List<String>,
    val generatedByAi: Boolean = false
)

data class PreapprovedOfferResponse(
    val inn: String,
    val maxAmount: BigDecimal,
    val termMonths: Int,
    val interestRate: Double,
    val monthlyPayment: BigDecimal,
    val overpayment: BigDecimal,
    val confidenceLevel: String
)