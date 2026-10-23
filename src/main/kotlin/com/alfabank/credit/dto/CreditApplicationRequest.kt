package com.alfabank.credit.dto

import java.math.BigDecimal
import java.util.UUID

data class CreateApplicationRequest(
    val companyId: UUID,
    val productId: UUID,
    val requestedAmount: BigDecimal,
    val requestedTermMonths: Int,
    val purpose: String? = null
)