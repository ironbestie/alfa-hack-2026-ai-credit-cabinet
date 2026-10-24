package com.alfabank.credit.dto

import com.alfabank.credit.domain.ApplicationStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CompanySummary(
    val id: UUID,
    val inn: String,
    val ogrn: String,
    val name: String,
    val category: String,
    val status: String
)

data class ProductSummary(
    val id: UUID,
    val name: String,
    val baseRate: Double
)

data class ApplicationResponse(
    val id: UUID,
    val status: ApplicationStatus,
    val company: CompanySummary,
    val product: ProductSummary,
    val requestedAmount: BigDecimal,
    val requestedTermMonths: Int,
    val purpose: String?,
    val hasCollateral: Boolean,
    val riskScore: Int?,
    val scoringDetails: String?,
    val approvedAmount: BigDecimal?,
    val approvedTermMonths: Int?,
    val rejectionReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
