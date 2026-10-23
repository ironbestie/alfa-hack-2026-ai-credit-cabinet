package com.alfabank.credit.service

import com.alfabank.credit.domain.*
import com.alfabank.credit.dto.*
import com.alfabank.credit.exception.NotFoundException
import com.alfabank.credit.integration.AiAssistantClient
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.ConsentRepository
import com.alfabank.credit.repository.CreditProductRepository
import com.alfabank.credit.scoring.GradeResolver
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
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
    private val sseService: SseService,
    private val accessService: ApplicationAccessService,
    private val gradeResolver: GradeResolver
) {
    @Transactional
    fun createApplication(request: CreateApplicationRequest): ApplicationResponse {
        val company = companyRepository.findById(request.companyId).orElseThrow {
            NotFoundException("Company not found")
        }
        val product = productRepository.findById(request.productId).orElseThrow {
            NotFoundException("Product not found")
        }
        if (request.requestedAmount < product.minAmount || request.requestedAmount > product.maxAmount) {
            throw IllegalArgumentException("Amount must be between ${product.minAmount} and ${product.maxAmount}")
        }
        if (request.requestedTermMonths < product.minTerm || request.requestedTermMonths > product.maxTerm) {
            throw IllegalArgumentException("Term must be between ${product.minTerm} and ${product.maxTerm} months")
        }

        val owner = accessService.currentUsername()
        val application = Application(
            company = company,
            product = product,
            requestedAmount = request.requestedAmount,
            requestedTermMonths = request.requestedTermMonths,
            purpose = request.purpose,
            ownerUsername = owner,
            status = ApplicationStatus.DRAFT
        )

        val saved = applicationRepository.save(application)
        eventService.saveEvent(saved, ApplicationEventType.APPLICATION_CREATED, "Заявка создана", owner)
        return mapToResponse(saved)
    }

    @Transactional
    fun signConsents(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)

        val existing = consentRepository.findByApplicationId(applicationId)
        var added = false
        if (existing.none { it.consentType == "BKI" }) {
            consentRepository.save(Consent(application = application, consentType = "BKI"))
            added = true
        }
        if (existing.none { it.consentType == "PD" }) {
            consentRepository.save(Consent(application = application, consentType = "PD"))
            added = true
        }
        if (added) {
            eventService.saveEvent(
                application,
                ApplicationEventType.CONSENT_SIGNED,
                "Подписаны согласия БКИ и ПДн",
                accessService.currentUsername()
            )
        }
    }

    @Transactional
    fun submitApplication(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.SUBMITTED)

        val consents = consentRepository.findByApplicationId(applicationId)
        if (consents.none { it.consentType == "BKI" } || consents.none { it.consentType == "PD" }) {
            throw IllegalStateException("Необходимо подписать согласия БКИ и ПДн перед отправкой")
        }

        application.status = ApplicationStatus.SUBMITTED
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        sseService.sendUpdate(applicationId.toString(), "SUBMITTED", "Заявка отправлена")
        eventService.saveEvent(
            application,
            ApplicationEventType.STATUS_CHANGED,
            "Заявка отправлена на рассмотрение",
            accessService.currentUsername()
        )

        eventPublisher.publishEvent(ApplicationSubmittedEvent(applicationId))
    }

    @Transactional(readOnly = true)
    fun getAllApplications(page: Int, size: Int): PageResponse<ApplicationResponse> {
        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val applications = if (accessService.isManager()) {
            applicationRepository.findAll(pageable)
        } else {
            applicationRepository.findByOwnerUsername(accessService.currentUsername(), pageable)
        }
        return PageResponse.from(applications.map { mapToResponse(it) })
    }

    @Transactional(readOnly = true)
    fun getApplicationById(id: UUID): ApplicationResponse {
        val application = applicationRepository.findById(id).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)
        return mapToResponse(application)
    }

    @Transactional(readOnly = true)
    fun checkAccess(applicationId: UUID) {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)
    }

    @Transactional(readOnly = true)
    fun getPendingApplicationsForManager(page: Int, size: Int): PageResponse<Map<String, Any?>> {
        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "updatedAt"))
        val applications = applicationRepository.findByStatus(ApplicationStatus.MANUAL_REVIEW, pageable)
        return PageResponse.from(applications.map {
            mapOf(
                "id" to it.id,
                "amount" to it.requestedAmount,
                "score" to it.riskScore,
                "reason" to it.scoringDetails
            )
        })
    }

    @Transactional(readOnly = true)
    fun getExplanation(applicationId: UUID): ExplanationResponse {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)

        val reasons = mutableListOf<String>()
        val nextSteps = mutableListOf<String>()

        if (application.scoringDetails != null) {
            reasons.addAll(application.scoringDetails!!.split("; ").filter { it.isNotBlank() })
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

        return ExplanationResponse(
            summary = aiSummary,
            reasons = reasons.ifEmpty { listOf("Стандартный риск") },
            nextSteps = nextSteps
        )
    }

    @Transactional(readOnly = true)
    fun getPreapprovedOffer(inn: String): PreapprovedOfferResponse {
        val company = companyRepository.findByInn(inn)
            ?: throw NotFoundException("Company not found")

        val maxAmount = company.annualRevenue.divide(BigDecimal("4")).min(BigDecimal("30000000"))

        return PreapprovedOfferResponse(
            inn = inn,
            maxAmount = maxAmount.max(BigDecimal("500000")),
            termMonths = 24,
            interestRate = 22.0,
            confidenceLevel = "PREAPPROVED"
        )
    }

    @Transactional(readOnly = true)
    fun getScore(applicationId: UUID): ScoreResponse {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)

        if (application.riskScore == null) {
            throw IllegalStateException("Скоринг для этой заявки еще не выполнен")
        }

        val factors = mutableListOf<ScoreFactorDto>()
        if (application.scoringDetails != null) {
            val parts = application.scoringDetails!!.split("; ")
            for (part in parts) {
                val match = Regex("^(.*) \\((-?\\d+)\\)$").find(part)
                if (match != null) {
                    val name = match.groupValues[1]
                    val contribution = match.groupValues[2].toIntOrNull() ?: 0
                    factors.add(ScoreFactorDto(name, contribution, ""))
                } else {
                    factors.add(ScoreFactorDto(part, 0, ""))
                }
            }
        }

        val grade = gradeResolver.gradeFor(application.riskScore!!)

        return ScoreResponse(
            score = application.riskScore!!,
            grade = grade,
            riskLevel = gradeResolver.riskLevel(grade),
            factors = factors
        )
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
}
