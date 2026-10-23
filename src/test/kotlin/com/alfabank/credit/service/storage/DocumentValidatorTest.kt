package com.alfabank.credit.service.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.security.MessageDigest

class DocumentValidatorTest {

    private val validator = DocumentValidator()

    @Test
    fun `принимает PDF и возвращает метаданные`() {
        val content = "%PDF-1.7\ncontent".toByteArray()
        val file = MockMultipartFile("file", "statement.pdf", "application/pdf", content)

        val result = validator.validate(file)

        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        assertEquals("statement.pdf", result.originalFileName)
        assertEquals("pdf", result.extension)
        assertEquals("application/pdf", result.contentType)
        assertEquals(expectedSha256, result.sha256)
        assertTrue(result.sha256.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `принимает JPEG`() {
        val content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val jpgFile = MockMultipartFile("file", "photo.jpg", "image/jpeg", content)
        val jpegFile = MockMultipartFile("file", "photo.jpeg", "image/jpeg", content)

        assertEquals("image/jpeg", validator.validate(jpgFile).contentType)
        assertEquals("image/jpeg", validator.validate(jpegFile).contentType)
    }

    @Test
    fun `принимает PNG`() {
        val content = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00
        )
        val file = MockMultipartFile("file", "scan.png", "image/png", content)

        val result = validator.validate(file)

        assertEquals("image/png", result.contentType)
    }

    @Test
    fun `отклоняет неподдерживаемое расширение`() {
        val file = MockMultipartFile(
            "file",
            "script.exe",
            "application/octet-stream",
            byteArrayOf(0x4D, 0x5A)
        )

        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(file)
        }
    }

    @Test
    fun `отклоняет несоответствие расширения и содержимого`() {
        val file = MockMultipartFile(
            "file",
            "fake.pdf",
            "application/pdf",
            "not a pdf".toByteArray()
        )

        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(file)
        }
    }

    @Test
    fun `отклоняет пустой файл`() {
        val file = MockMultipartFile("file", "empty.pdf", "application/pdf", ByteArray(0))

        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(file)
        }
    }

    @Test
    fun `отклоняет файл больше 10 МБ`() {
        val file = MockMultipartFile(
            "file",
            "large.pdf",
            "application/pdf",
            ByteArray(10 * 1024 * 1024 + 1)
        )

        assertThrows(IllegalArgumentException::class.java) {
            validator.validate(file)
        }
    }

    @Test
    fun `убирает путь из оригинального имени`() {
        val content = "%PDF-1.7\ncontent".toByteArray()
        val file = MockMultipartFile("file", "C:\\fakepath\\statement.pdf", "application/pdf", content)

        val result = validator.validate(file)

        assertEquals("statement.pdf", result.originalFileName)
    }
}
