package com.alfabank.credit.dto

import java.time.Instant
import java.util.UUID

data class DeadLetterResponse(
    val applicationId: UUID,
    val attempts: Int,
    val lastError: String?,
    val updatedAt: Instant
)
