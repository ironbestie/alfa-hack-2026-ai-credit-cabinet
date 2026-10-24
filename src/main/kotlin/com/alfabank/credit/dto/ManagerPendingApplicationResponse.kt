package com.alfabank.credit.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ManagerPendingApplicationResponse(
    val id: UUID,
    val company: CompanySummary,
    val amount: BigDecimal,
    val score: Int?,
    val grade: String?,
    val reason: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
