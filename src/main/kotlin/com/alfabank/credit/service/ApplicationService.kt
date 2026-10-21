package com.alfabank.credit.service

import com.alfabank.credit.domain.*
import com.alfabank.credit.dto.ApplicationResponse
import com.alfabank.credit.dto.CreateApplicationRequest
import com.alfabank.credit.integration.AiAssistantClient
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.ConsentRepository
import com.alfabank.credit.repository.CreditProductRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ApplicationService(
    private val applicationRepository: ApplicationRepository,
    private val companyRepository: CompanyRepository,
    private val productRepository: CreditProductRepository,
    private val consentRepository: ConsentRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventService: ApplicationEventService,
    private val aiAssistantClient: AiAssistantClient,
    private val sseService: SseService
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
        eventService.saveEvent(saved, ApplicationEventType.APPLICATION_CREATED, "Заявка создана")
        return mapToResponse(saved)
    }

    @Transactional
    fun signConsents(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }
        val existing = consentRepository.findByApplicationId(applicationId)
        if (existing.none { it.consentType == "BKI" }) {
            consentRepository.save(Consent(application = application, consentType = "BKI"))
        }
        if (existing.none { it.consentType == "PD" }) {
            consentRepository.save(Consent(application = application, consentType = "PD"))
        }
    }

    @Transactional
    fun submitApplication(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SUBMITTED)

        val consents = consentRepository.findByApplicationId(applicationId)
        if (consents.none { it.consentType == "BKI" } || consents.none { it.consentType == "PD" }) {
            throw IllegalStateException("Необходимо подписать согласия БКИ и ПДн перед отправкой")
        }

        application.status = ApplicationStatus.SUBMITTED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        sseService.sendUpdate(applicationId.toString(), "SUBMITTED", "Заявка отправлена")
        eventService.saveEvent(application, ApplicationEventType.STATUS_CHANGED, "Заявка отправлена на рассмотрение")

        eventPublisher.publishEvent(ApplicationSubmittedEvent(applicationId))
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

    @Transactional(readOnly = true)
    fun getPendingApplicationsForManager(): List<Map<String, Any?>> {
        return applicationRepository.findAll()
            .filter { it.status == ApplicationStatus.MANUAL_REVIEW }
            .map {
                mapOf(
                    "id" to it.id,
                    "amount" to it.requestedAmount,
                    "score" to it.riskScore,
                    "reason" to it.scoringDetails
                )
            }
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


    @Transactional(readOnly = true)
    fun getExplanation(applicationId: UUID): com.alfabank.credit.dto.ExplanationResponse {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            IllegalArgumentException("Application not found")
        }

        val reasons = mutableListOf<String>()
        val nextSteps = mutableListOf<String>()

        if (application.scoringDetails != null) {
            reasons.addAll(application.scoringDetails!!.split(", ").filter { it.isNotBlank() })
        }

        if (application.status == ApplicationStatus.REJECTED || application.status == ApplicationStatus.COUNTER_OFFER) {
            nextSteps.add("Погасите мелкие кредиты, и нагрузка снизится.")
            nextSteps.add("Вернитесь через 3 месяца: мы пересчитаем лимит автоматически.")
        } else if (application.status == ApplicationStatus.APPROVED) {
            nextSteps.add("Вы можете принять предложение и подписать документы.")
        }

        val factsForAi = "Статус заявки: ${application.status}. " +
                "Причины: ${reasons.joinToString(", ")}. " +
                "Запрошенная сумма: ${application.requestedAmount}."

        val prompt = "Ты вежливый банковский ассистент. На основе этих данных напиши клиенту краткое объяснение решения (1-2 предложения). " +
                "Не используй сложные банковские термины. Данные: $factsForAi"

        val aiSummary = aiAssistantClient.generateExplanation(prompt)

        return com.alfabank.credit.dto.ExplanationResponse(
            summary = aiSummary,
            reasons = reasons.ifEmpty { listOf("Стандартный риск") },
            nextSteps = nextSteps
        )
    }

    @Transactional(readOnly = true)
    fun getPreapprovedOffer(inn: String): com.alfabank.credit.dto.PreapprovedOfferResponse {
        val company = companyRepository.findAll().find { it.inn == inn }
            ?: throw IllegalArgumentException("Company not found")

        val maxAmount = company.annualRevenue.divide(java.math.BigDecimal("4")).min(java.math.BigDecimal("30000000"))

        return com.alfabank.credit.dto.PreapprovedOfferResponse(
            inn = inn,
            maxAmount = maxAmount.max(java.math.BigDecimal("500000")),
            termMonths = 24,
            interestRate = 22.0,
            confidenceLevel = "PREAPPROVED"
        )
    }
}