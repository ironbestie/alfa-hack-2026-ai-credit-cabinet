package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Document
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.DocumentRepository
import com.alfabank.credit.service.storage.DocumentStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@Service
class ApplicationDocumentService(
    private val applicationRepository: ApplicationRepository,
    private val documentRepository: DocumentRepository,
    private val documentStorage: DocumentStorage,
    private val eventService: ApplicationEventService
) {
    @Transactional
    fun uploadDocument(applicationId: UUID, file: MultipartFile) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        val fileName = documentStorage.store(file, applicationId.toString())
        documentRepository.save(Document(application = application, fileName = fileName))

        if (application.status == ApplicationStatus.NEEDS_MORE_INFO) {
            application.status = ApplicationStatus.SCORING
            application.updatedAt = Instant.now()
            applicationRepository.save(application)
            eventService.saveEvent(application, ApplicationEventType.DOCUMENT_REQUESTED, "Документ загружен, заявка возвращена в работу")
        }
    }
}