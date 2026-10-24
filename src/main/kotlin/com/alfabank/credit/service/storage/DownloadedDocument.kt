package com.alfabank.credit.service.storage

data class DownloadedDocument(
    val content: ByteArray,
    val contentType: String,
    val originalFileName: String
)
