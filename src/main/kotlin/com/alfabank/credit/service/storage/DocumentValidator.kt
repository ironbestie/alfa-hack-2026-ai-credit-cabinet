package com.alfabank.credit.service.storage

import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest

@Component
class DocumentValidator {

    fun validate(file: MultipartFile): ValidatedDocument {
        if (file.isEmpty) throw IllegalArgumentException("File is empty")
        if (file.size > MAX_FILE_SIZE) throw IllegalArgumentException("File size exceeds 10MB")

        val originalFileName = file.originalFilename
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("File name is missing")
        if (originalFileName.length > 255) throw IllegalArgumentException("File name is too long")

        val extension = originalFileName.substringAfterLast('.', "").lowercase()
        val contentType = ALLOWED_CONTENT_TYPES[extension]
            ?: throw IllegalArgumentException("Unsupported file type. Allowed: PDF, JPG, JPEG, PNG")

        val digest = MessageDigest.getInstance("SHA-256")
        val header = ByteArray(HEADER_SIZE)
        var headerLength = 0

        file.inputStream.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break

                if (headerLength < header.size) {
                    val copyLength = minOf(read, header.size - headerLength)
                    System.arraycopy(buffer, 0, header, headerLength, copyLength)
                    headerLength += copyLength
                }

                digest.update(buffer, 0, read)
            }
        }

        if (!matchesSignature(extension, header, headerLength)) {
            throw IllegalArgumentException("File content does not match its extension")
        }

        return ValidatedDocument(
            originalFileName = originalFileName,
            extension = extension,
            contentType = contentType,
            sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        )
    }

    private fun matchesSignature(extension: String, header: ByteArray, length: Int): Boolean {
        return when (extension) {
            "pdf" -> containsPdfHeader(header, length)
            "jpg", "jpeg" -> length >= JPEG_SIGNATURE.size &&
                JPEG_SIGNATURE.indices.all { header[it] == JPEG_SIGNATURE[it] }
            "png" -> length >= PNG_SIGNATURE.size &&
                PNG_SIGNATURE.indices.all { header[it] == PNG_SIGNATURE[it] }
            else -> false
        }
    }

    private fun containsPdfHeader(header: ByteArray, length: Int): Boolean {
        if (length < PDF_HEADER.size) return false

        for (offset in 0..(length - PDF_HEADER.size)) {
            if (PDF_HEADER.indices.all { header[offset + it] == PDF_HEADER[it] }) return true
        }

        return false
    }

    companion object {
        private const val MAX_FILE_SIZE = 10L * 1024 * 1024
        private const val HEADER_SIZE = 1024
        private const val BUFFER_SIZE = 8192
        private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
        private val ALLOWED_CONTENT_TYPES = mapOf(
            "pdf" to "application/pdf",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png"
        )
    }
}

data class ValidatedDocument(
    val originalFileName: String,
    val extension: String,
    val contentType: String,
    val sha256: String
)
