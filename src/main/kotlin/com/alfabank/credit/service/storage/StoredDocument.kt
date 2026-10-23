package com.alfabank.credit.service.storage

data class StoredDocument(
    val key: String,
    val originalFileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val sha256: String
)
