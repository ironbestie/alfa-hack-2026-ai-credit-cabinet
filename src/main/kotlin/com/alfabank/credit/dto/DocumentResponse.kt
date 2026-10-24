package com.alfabank.credit.dto

import java.time.Instant
import java.util.UUID

data class DocumentResponse(
    val id: UUID,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val uploadDate: Instant,
    val downloadUrl: String
)
