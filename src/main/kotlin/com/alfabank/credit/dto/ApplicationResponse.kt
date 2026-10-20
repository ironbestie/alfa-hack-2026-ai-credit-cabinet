package com.alfabank.credit.dto

import com.alfabank.credit.domain.ApplicationStatus
import java.math.BigDecimal
import java.util.UUID

data class ApplicationResponse(
    val id: UUID,
    val status: ApplicationStatus,
    val requestedAmount: BigDecimal,
    val requestedTermMonths: Int
)