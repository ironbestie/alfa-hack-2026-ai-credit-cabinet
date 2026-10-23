package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.ApplicationSubmittedEvent
import com.alfabank.credit.domain.Document
import com.alfabank.credit.exception.NotFoundException
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.DocumentRepository
import com.alfabank.credit.service.storage.DocumentStorage
import org.springframework.context.ApplicationEventPublisher
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
    private val eventPublisher: ApplicationEventPublisher,
    private val accessService: ApplicationAccessService
) {
    @Transactional
    fun uploadDocument(applicationId: UUID, file: MultipartFile) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)

        val fileName = documentStorage.store(file, applicationId.toString())
        documentRepository.save(Document(application = application, fileName = fileName))

        val actor = accessService.currentUsername()
        if (application.status == ApplicationStatus.NEEDS_MORE_INFO) {
            // Возвращаем заявку в работу и перезапускаем конвейер, иначе она зависнет в SCORING.
            ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SUBMITTED)
            application.status = ApplicationStatus.SUBMITTED
            application.updatedAt = Instant.now()
            applicationRepository.save(application)
            eventService.saveEvent(
                application,
                ApplicationEventType.DOCUMENT_UPLOADED,
                "Документ загружен, заявка возвращена в работу",
                actor
            )
            eventPublisher.publishEvent(ApplicationSubmittedEvent(applicationId))
        } else {
            eventService.saveEvent(
                application,
                ApplicationEventType.DOCUMENT_UPLOADED,
                "Загружен документ: $fileName",
                actor
            )
        }
    }
}
