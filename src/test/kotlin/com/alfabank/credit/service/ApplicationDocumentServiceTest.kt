package com.alfabank.credit.service

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.exception.InvalidStateTransitionException
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.DocumentRepository
import com.alfabank.credit.service.storage.DocumentStorage
import com.alfabank.credit.service.storage.StoredDocument
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.util.UUID

class ApplicationDocumentServiceTest {

    private val applicationRepository = mock(ApplicationRepository::class.java)
    private val documentRepository = mock(DocumentRepository::class.java)
    private val documentStorage = mock(DocumentStorage::class.java)
    private val eventService = mock(ApplicationEventService::class.java)
    private val sseService = mock(SseService::class.java)
    private val accessService = mock(ApplicationAccessService::class.java)

    private val service = ApplicationDocumentService(
        applicationRepository,
        documentRepository,
        documentStorage,
        eventService,
        sseService,
        accessService
    )

    @Test
    fun `запрещает загрузку вне NEEDS_MORE_INFO`() {
        val applicationId = UUID.randomUUID()
        val application = mock(Application::class.java)
        `when`(application.status).thenReturn(ApplicationStatus.DRAFT)
        `when`(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application))

        val file = MockMultipartFile(
            "file",
            "statement.pdf",
            "application/pdf",
            "%PDF-1.7\ncontent".toByteArray()
        )

        assertThrows(InvalidStateTransitionException::class.java) {
            service.uploadDocument(applicationId, file)
        }

        verifyNoInteractions(documentStorage)
    }

    @Test
    fun `сохраняет метаданные документа и возвращает заявку менеджеру`() {
        val applicationId = UUID.randomUUID()
        val application = mock(Application::class.java)
        `when`(application.status).thenReturn(ApplicationStatus.NEEDS_MORE_INFO)
        `when`(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application))
        `when`(accessService.currentUsername()).thenReturn("client")

        val file = MockMultipartFile(
            "file",
            "statement.pdf",
            "application/pdf",
            "%PDF-1.7\ncontent".toByteArray()
        )
        val storedDocument = StoredDocument(
            key = "applications/$applicationId/document.pdf",
            originalFileName = "statement.pdf",
            contentType = "application/pdf",
            sizeBytes = file.size,
            sha256 = "a".repeat(64)
        )
        `when`(documentStorage.store(file, applicationId.toString())).thenReturn(storedDocument)

        service.uploadDocument(applicationId, file)

        verify(documentRepository).save(argThat {
            it.fileName == storedDocument.key &&
                it.originalFileName == storedDocument.originalFileName &&
                it.contentType == storedDocument.contentType &&
                it.sizeBytes == storedDocument.sizeBytes &&
                it.sha256 == storedDocument.sha256
        })
        verify(applicationRepository).save(application)
        verify(eventService).saveEvent(
            application,
            ApplicationEventType.DOCUMENT_UPLOADED,
            "Документ загружен: statement.pdf, заявка возвращена в очередь менеджера",
            "client"
        )
        verify(sseService).sendUpdate(
            applicationId.toString(),
            "MANUAL_REVIEW",
            "Документ загружен, заявка возвращена менеджеру"
        )
    }
}
