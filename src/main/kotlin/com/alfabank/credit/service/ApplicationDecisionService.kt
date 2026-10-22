package com.alfabank.credit.service

import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.dto.OfferResponse
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.OfferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class ApplicationDecisionService(
    private val applicationRepository: ApplicationRepository,
    private val offerRepository: OfferRepository,
    private val eventService: ApplicationEventService
) {
    @Transactional
    fun acceptOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow { IllegalArgumentException("Application not found") }
        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SIGNING)
        application.status = ApplicationStatus.SIGNING
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        eventService.saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Клиент принял предложение")
    }

    @Transactional
    fun signOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow { IllegalArgumentException("Application not found") }
        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.FUNDED)
        application.status = ApplicationStatus.FUNDED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        eventService.saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Кредит выдан")
    }

    @Transactional(readOnly = true)
    fun getOffers(applicationId: UUID): List<OfferResponse> {
        return offerRepository.findByApplicationId(applicationId).map {
            OfferResponse(
                id = it.application.id!!,
                amount = it.amount,
                termMonths = it.termMonths,
                interestRate = it.interestRate,
                type = it.type
            )
        }
    }

    @Transactional
    fun processManagerDecision(applicationId: UUID, decisionType: String, approvedAmount: BigDecimal?, approvedTermMonths: Int?, comment: String) {
        val application = applicationRepository.findById(applicationId).orElseThrow { IllegalArgumentException("Application not found") }
        when (decisionType) {
            "APPROVE" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.APPROVED)
                application.status = ApplicationStatus.APPROVED
                application.approvedAmount = application.requestedAmount
                application.approvedTermMonths = application.requestedTermMonths
            }
            "COUNTER_OFFER" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.COUNTER_OFFER)
                application.status = ApplicationStatus.COUNTER_OFFER
                application.approvedAmount = approvedAmount ?: application.requestedAmount
                application.approvedTermMonths = approvedTermMonths ?: application.requestedTermMonths
            }
            "REJECT" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.REJECTED)
                application.status = ApplicationStatus.REJECTED
                application.rejectionReason = comment
            }
            else -> throw IllegalArgumentException("Unknown decision type")
        }
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        eventService.saveEvent(application, ApplicationEventType.DECISION_MADE, "Менеджер: $decisionType")
    }
}