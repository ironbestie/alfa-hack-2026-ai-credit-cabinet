package com.alfabank.credit.service.storage

import org.springframework.web.multipart.MultipartFile

interface DocumentStorage {
    fun store(file: MultipartFile, applicationId: String): StoredDocument
    fun download(key: String): ByteArray
    fun delete(key: String)
}