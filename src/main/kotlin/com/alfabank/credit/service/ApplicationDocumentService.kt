package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Document
import com.alfabank.credit.dto.DocumentResponse
import com.alfabank.credit.exception.InvalidStateTransitionException
import com.alfabank.credit.exception.NotFoundException
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.DocumentRepository
import com.alfabank.credit.service.storage.DocumentStorage
import com.alfabank.credit.service.storage.DownloadedDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    private val log = LoggerFactory.getLogger(ApplicationDocumentService::class.java)

    @Transactional
    fun uploadDocument(applicationId: UUID, file: MultipartFile) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Заявка не найдена") }
        accessService.checkAccess(application)

        if (application.status != ApplicationStatus.NEEDS_MORE_INFO) {
            throw InvalidStateTransitionException(
                "Загрузить документ можно только в статусе NEEDS_MORE_INFO"
            )
        }

        val storedDocument = documentStorage.store(file, applicationId.toString())
        registerRollbackCompensation(storedDocument.key)

        try {
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
        } catch (e: Exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                compensate(storedDocument.key)
            }
            throw e
        }

        sseService.sendUpdate(
            applicationId.toString(),
            "MANUAL_REVIEW",
            "Документ загружен, заявка возвращена менеджеру"
        )
    }

    @Transactional(readOnly = true)
    fun listDocuments(applicationId: UUID): List<DocumentResponse> {
        checkApplicationAccess(applicationId)
        return documentRepository.findByApplication_IdOrderByUploadDateAsc(applicationId)
            .map { toResponse(applicationId, it) }
    }

    @Transactional(readOnly = true)
    fun downloadDocument(applicationId: UUID, documentId: UUID): DownloadedDocument {
        checkApplicationAccess(applicationId)
        val document = documentRepository.findByIdAndApplication_Id(documentId, applicationId)
            ?: throw NotFoundException("Документ не найден")

        return DownloadedDocument(
            content = documentStorage.download(document.fileName),
            contentType = document.contentType ?: "application/octet-stream",
            originalFileName = document.originalFileName ?: document.fileName.substringAfterLast('/')
        )
    }

    private fun checkApplicationAccess(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Заявка не найдена") }
        accessService.checkAccess(application)
    }

    private fun toResponse(applicationId: UUID, document: Document): DocumentResponse {
        val documentId = document.id
            ?: throw IllegalStateException("У документа отсутствует идентификатор")

        return DocumentResponse(
            id = documentId,
            originalFileName = document.originalFileName ?: document.fileName.substringAfterLast('/'),
            contentType = document.contentType ?: "application/octet-stream",
            sizeBytes = document.sizeBytes,
            sha256 = document.sha256,
            uploadDate = document.uploadDate,
            downloadUrl = "/api/applications/$applicationId/documents/$documentId/download"
        )
    }

    private fun registerRollbackCompensation(key: String) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        compensate(key)
                    }
                }
            }
        )
    }

    private fun compensate(key: String) {
        runCatching { documentStorage.delete(key) }
            .onFailure { log.error("Не удалось удалить S3-объект после отката транзакции: $key", it) }
    }
}
