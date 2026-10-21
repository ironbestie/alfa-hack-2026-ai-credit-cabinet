package com.alfabank.credit.service.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.UUID

@Service
class LocalFileStorage(
    @Value("\${app.storage.location:./uploads}") private val storagePath: String
) : DocumentStorage {
    private val log = LoggerFactory.getLogger(LocalFileStorage::class.java)

    override fun store(file: MultipartFile, applicationId: String): String {
        val dir = File(storagePath)
        if (!dir.exists()) dir.mkdirs()

        val fileName = "${applicationId}_${UUID.randomUUID()}_${file.originalFilename}"
        val targetFile = File(dir, fileName)
        file.transferTo(targetFile.absoluteFile)
        log.info("Document saved to: ${targetFile.absolutePath}")
        return fileName
    }
}