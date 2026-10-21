package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
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

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SIGNING)

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

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.FUNDED)

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

        when (decisionType) {
            "APPROVE" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.APPROVED)
                application.status = ApplicationStatus.APPROVED
                application.approvedAmount = application.requestedAmount
                application.approvedTermMonths = application.requestedTermMonths
                application.rejectionReason = "Одобрено менеджером: $comment"
            }
            "COUNTER_OFFER" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.COUNTER_OFFER)
                application.status = ApplicationStatus.COUNTER_OFFER
                application.approvedAmount = approvedAmount ?: application.requestedAmount
                application.approvedTermMonths = approvedTermMonths ?: application.requestedTermMonths
                application.rejectionReason = "Контрпредложение менеджера: $comment"
            }
            "REJECT" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.REJECTED)
                application.status = ApplicationStatus.REJECTED
                application.rejectionReason = "Отклонено менеджером: $comment"
            }
            "REQUEST_DOCS" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.NEEDS_MORE_INFO)
                application.status = ApplicationStatus.NEEDS_MORE_INFO
                application.rejectionReason = "Запрошены документы: $comment"
            }
            else -> throw IllegalArgumentException("Unknown decision type")
        }

        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val action = when (decisionType) {
            "APPROVE" -> "Одобрено"
            "COUNTER_OFFER" -> "Контрпредложение"
            "REQUEST_DOCS" -> "Запрошены документы"
            else -> "Отклонено"
        }
        eventService.saveEvent(application, ApplicationEventType.DECISION_MADE, "Менеджер вынес решение: $action")
    }

    @Transactional(readOnly = true)
    fun getOffers(applicationId: UUID): List<com.alfabank.credit.dto.OfferResponse> {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        val list = mutableListOf<com.alfabank.credit.dto.OfferResponse>()

        if (application.approvedAmount != null) {
            list.add(
                com.alfabank.credit.dto.OfferResponse(
                    id = application.id!!,
                    amount = application.approvedAmount!!,
                    termMonths = application.approvedTermMonths ?: application.requestedTermMonths,
                    interestRate = application.product.baseRate,
                    type = if (application.status == ApplicationStatus.COUNTER_OFFER) "COUNTER_OFFER" else "STANDARD"
                )
            )
        }

        if (application.status == ApplicationStatus.REJECTED || application.status == ApplicationStatus.COUNTER_OFFER) {
            list.add(
                com.alfabank.credit.dto.OfferResponse(
                    id = application.id!!,
                    amount = application.requestedAmount.multiply(java.math.BigDecimal("0.8")),
                    termMonths = application.requestedTermMonths + 6,
                    interestRate = 19.5,
                    type = "WITH_GUARANTEE"
                )
            )
        }

        return list
    }
}