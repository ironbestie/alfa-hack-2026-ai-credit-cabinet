package com.alfabank.credit.service

import com.alfabank.credit.domain.*
import com.alfabank.credit.dto.ApplicationResponse
import com.alfabank.credit.dto.CreateApplicationRequest
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.CreditProductRepository
import com.alfabank.credit.repository.DocumentRepository
import com.alfabank.credit.service.storage.DocumentStorage
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@Service
class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val companyRepository: CompanyRepository,
    private val productRepository: CreditProductRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val documentRepository: DocumentRepository,
    private val documentStorage: DocumentStorage,
    private val eventRepository: ApplicationEventRepository
) {

    @Transactional
    fun createApplication(request: CreateApplicationRequest): ApplicationResponse {
        val company = companyRepository.findById(request.companyId).orElseThrow {
            IllegalArgumentException("Company not found")
        }

        val product = productRepository.findById(request.productId).orElseThrow {
            IllegalArgumentException("Product not found")
        }

        if (request.requestedAmount < product.minAmount || request.requestedAmount > product.maxAmount) {
            throw IllegalArgumentException("Amount must be between ${product.minAmount} and ${product.maxAmount}")
        }

        val application = Application(
            company = company,
            product = product,
            requestedAmount = request.requestedAmount,
            requestedTermMonths = request.requestedTermMonths,
            status = ApplicationStatus.DRAFT
        )

        val saved = applicationRepository.save(application)
        saveEvent(saved, ApplicationEventType.APPLICATION_CREATED, "Заявка создана")

        return mapToResponse(saved)
    }

    @Transactional
    fun submitApplication(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.DRAFT) {
            throw IllegalStateException("Only DRAFT applications can be submitted")
        }

        application.status = ApplicationStatus.SUBMITTED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Заявка отправлена на рассмотрение")
        eventPublisher.publishEvent(ApplicationSubmittedEvent(applicationId))
    }

    @Transactional
    fun acceptOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.DECIDED || application.approvedAmount == null) {
            throw IllegalStateException("Application has no approved offer to accept")
        }

        application.status = ApplicationStatus.DISBURSED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Клиент принял условия. Кредит выдан на сумму ${application.approvedAmount} ₽")
    }

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
            saveEvent(application, ApplicationEventType.DOCUMENT_REQUESTED, "Документ загружен, заявка возвращена в работу")
        }
    }

    @Transactional
    fun processManagerDecision(applicationId: UUID, isApproved: Boolean, comment: String) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.MANUAL_REVIEW) {
            throw IllegalStateException("Application is not in MANUAL_REVIEW status")
        }

        if (isApproved) {
            application.status = ApplicationStatus.DECIDED
            application.approvedAmount = application.requestedAmount
            application.rejectionReason = "Одобрено менеджером: $comment"
        } else {
            application.status = ApplicationStatus.DECIDED
            application.rejectionReason = "Отклонено менеджером: $comment"
        }
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        saveEvent(application, ApplicationEventType.DECISION_MADE, "Менеджер вынес решение: ${if (isApproved) "Одобрено" else "Отклонено"}")
    }

    @Transactional(readOnly = true)
    fun getAllApplications(): List<ApplicationResponse> {
        return applicationRepository.findAll().map { mapToResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getApplicationById(id: UUID): ApplicationResponse {
        val application = applicationRepository.findById(id).orElseThrow {
            IllegalArgumentException("Application not found")
        }
        return mapToResponse(application)
    }

    private fun mapToResponse(application: Application): ApplicationResponse {
        return ApplicationResponse(
            id = application.id!!,
            status = application.status,
            requestedAmount = application.requestedAmount,
            requestedTermMonths = application.requestedTermMonths,
            riskScore = application.riskScore,
            scoringDetails = application.scoringDetails,
            approvedAmount = application.approvedAmount,
            rejectionReason = application.rejectionReason
        )
    }

    private fun saveEvent(application: Application, type: ApplicationEventType, description: String) {
        val event = ApplicationEvent(
            application = application,
            eventType = type,
            description = description
        )
        eventRepository.save(event)
    }



    @Transactional(readOnly = true)
    fun getApplicationTimeline(applicationId: UUID): List<com.alfabank.credit.dto.EventResponse> {
        if (!applicationRepository.existsById(applicationId)) {
            throw IllegalArgumentException("Application not found")
        }

        return eventRepository.findByApplicationIdOrderByTimestampAsc(applicationId).map {
            com.alfabank.credit.dto.EventResponse(
                timestamp = it.timestamp,
                eventType = it.eventType.name,
                description = it.description,
                actor = it.actor
            )
        }
    }
}