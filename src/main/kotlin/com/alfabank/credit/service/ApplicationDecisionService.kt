package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.repository.ApplicationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ApplicationDecisionService(
    private val applicationRepository: ApplicationRepository,
    private val eventService: ApplicationEventService
) {
    @Transactional
    fun acceptOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.APPROVED && application.status != ApplicationStatus.COUNTER_OFFER) {
            throw IllegalStateException("Application has no approved offer to accept")
        }

        application.status = ApplicationStatus.SIGNING
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        eventService.saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Клиент принял предложение, переходим к подписанию")
    }

    @Transactional
    fun signOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.SIGNING) {
            throw IllegalStateException("Application is not in SIGNING status")
        }

        application.status = ApplicationStatus.FUNDED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        eventService.saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Документы подписаны. Кредит выдан на сумму ${application.approvedAmount} ₽")
    }

    @Transactional
    fun processManagerDecision(
        applicationId: UUID,
        decisionType: String,
        approvedAmount: BigDecimal?,
        approvedTermMonths: Int?,
        comment: String
    ) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        if (application.status != ApplicationStatus.MANUAL_REVIEW) {
            throw IllegalStateException("Application is not in MANUAL_REVIEW status")
        }

        when (decisionType) {
            "APPROVE" -> {
                application.status = ApplicationStatus.APPROVED
                application.approvedAmount = application.requestedAmount
                application.approvedTermMonths = application.requestedTermMonths
                application.rejectionReason = "Одобрено менеджером: $comment"
            }
            "COUNTER_OFFER" -> {
                application.status = ApplicationStatus.COUNTER_OFFER
                application.approvedAmount = approvedAmount ?: application.requestedAmount
                application.approvedTermMonths = approvedTermMonths ?: application.requestedTermMonths
                application.rejectionReason = "Контрпредложение менеджера: $comment"
            }
            "REJECT" -> {
                application.status = ApplicationStatus.REJECTED
                application.rejectionReason = "Отклонено менеджером: $comment"
            }
            else -> throw IllegalArgumentException("Unknown decision type")
        }

        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val action = when (decisionType) {
            "APPROVE" -> "Одобрено"
            "COUNTER_OFFER" -> "Контрпредложение"
            else -> "Отклонено"
        }
        eventService.saveEvent(application, ApplicationEventType.DECISION_MADE, "Менеджер вынес решение: $action")
    }
}