package com.alfabank.credit.service

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Offer
import com.alfabank.credit.dto.OfferResponse
import com.alfabank.credit.exception.NotFoundException
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
    private val eventService: ApplicationEventService,
    private val accessService: ApplicationAccessService
) {
    @Transactional
    fun acceptOffer(applicationId: UUID, offerId: UUID? = null) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)
        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SIGNING)

        val offers = offerRepository.findByApplicationId(applicationId)
        val selectedOffer = if (offerId != null) {
            offers.find { it.id == offerId } ?: throw NotFoundException("Offer not found for this application")
        } else {
            offers.firstOrNull()
        }

        selectedOffer?.let {
            application.approvedAmount = it.amount
            application.approvedTermMonths = it.termMonths
        }

        application.status = ApplicationStatus.SIGNING
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val description = selectedOffer?.let { "Клиент принял предложение ${it.type}" } ?: "Клиент принял предложение"
        eventService.saveEvent(
            application,
            ApplicationEventType.STATUS_CHANGED,
            description,
            accessService.currentUsername()
        )
    }

    @Transactional
    fun signOffer(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)
        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.FUNDED)
        application.status = ApplicationStatus.FUNDED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        eventService.saveEvent(
            application,
            ApplicationEventType.STATUS_CHANGED,
            "Кредит выдан",
            accessService.currentUsername()
        )
    }

    @Transactional(readOnly = true)
    fun getOffers(applicationId: UUID): List<OfferResponse> {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)

        return offerRepository.findByApplicationId(applicationId).map {
            OfferResponse(
                id = it.id!!,
                amount = it.amount,
                termMonths = it.termMonths,
                interestRate = it.interestRate,
                type = it.type
            )
        }
    }

    @Transactional
    fun processManagerDecision(
        applicationId: UUID,
        decisionType: String,
        approvedAmount: BigDecimal?,
        approvedTermMonths: Int?,
        comment: String
    ) {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }

        var offerType: String? = null
        var offerAmount: BigDecimal? = null
        var offerTerm: Int? = null

        when (decisionType.uppercase()) {
            "APPROVE" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.APPROVED)
                application.status = ApplicationStatus.APPROVED
                application.approvedAmount = approvedAmount ?: application.requestedAmount
                application.approvedTermMonths = approvedTermMonths ?: application.requestedTermMonths
                offerType = "REQUESTED"
                offerAmount = application.approvedAmount
                offerTerm = application.approvedTermMonths
            }
            "COUNTER_OFFER" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.COUNTER_OFFER)
                application.status = ApplicationStatus.COUNTER_OFFER
                application.approvedAmount = approvedAmount ?: application.requestedAmount
                application.approvedTermMonths = approvedTermMonths ?: application.requestedTermMonths
                offerType = "LIMIT"
                offerAmount = application.approvedAmount
                offerTerm = application.approvedTermMonths
            }
            "REQUEST_DOCS" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.NEEDS_MORE_INFO)
                application.status = ApplicationStatus.NEEDS_MORE_INFO
            }
            "REJECT" -> {
                ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.REJECTED)
                application.status = ApplicationStatus.REJECTED
                application.rejectionReason = comment
            }
            else -> throw IllegalArgumentException(
                "Unknown decision type: $decisionType. Allowed: APPROVE, COUNTER_OFFER, REQUEST_DOCS, REJECT"
            )
        }

        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        if (offerType != null && offerAmount != null && offerTerm != null) {
            saveOffer(application, offerType, offerAmount, offerTerm)
        }

        eventService.saveEvent(
            application,
            ApplicationEventType.DECISION_MADE,
            "Менеджер: ${decisionType.uppercase()}. $comment",
            accessService.currentUsername()
        )
    }

    private fun saveOffer(application: Application, type: String, amount: BigDecimal, termMonths: Int) {
        offerRepository.save(
            Offer(
                application = application,
                amount = amount,
                termMonths = termMonths,
                interestRate = application.product.baseRate,
                type = type
            )
        )
    }
}
