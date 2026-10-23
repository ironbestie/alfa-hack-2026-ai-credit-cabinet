package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Document
import com.alfabank.credit.exception.InvalidStateTransitionException
import com.alfabank.credit.exception.NotFoundException
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
    private val eventService: ApplicationEventService,
    private val sseService: SseService,
    private val accessService: ApplicationAccessService
) {
    @Transactional
    fun uploadDocument(applicationId: UUID, file: MultipartFile) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)

        if (application.status != ApplicationStatus.NEEDS_MORE_INFO) {
            throw InvalidStateTransitionException(
                "Загрузить документ можно только в статусе NEEDS_MORE_INFO"
            )
        }

        val storedDocument = documentStorage.store(file, applicationId.toString())
        documentRepository.save(
            Document(
                application = application,
                fileName = storedDocument.key,
                originalFileName = storedDocument.originalFileName,
                contentType = storedDocument.contentType,
                sizeBytes = storedDocument.sizeBytes,
                sha256 = storedDocument.sha256
            )
        )

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.MANUAL_REVIEW)
        application.status = ApplicationStatus.MANUAL_REVIEW
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val actor = accessService.currentUsername()
        eventService.saveEvent(
            application,
            ApplicationEventType.DOCUMENT_UPLOADED,
            "Документ загружен: ${storedDocument.originalFileName}, заявка возвращена в очередь менеджера",
            actor
        )
        sseService.sendUpdate(
            applicationId.toString(),
            "MANUAL_REVIEW",
            "Документ загружен, заявка возвращена менеджеру"
        )
    }
}
