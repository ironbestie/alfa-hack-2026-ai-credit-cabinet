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
import com.alfabank.credit.scoring.OfferMath
import com.alfabank.credit.service.scoring.ScoringDetailsCodec
import com.alfabank.credit.service.scoring.ScoringFactorRecord
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
    private val gradeResolver: GradeResolver,
    private val scoringDetailsCodec: ScoringDetailsCodec
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
            hasCollateral = request.hasCollateral,
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

        ApplicationStateMachine.validateTransition(application.status, ApplicationStatus.ENRICHMENT)

        val consents = consentRepository.findByApplicationId(applicationId)
        if (consents.none { it.consentType == "BKI" } || consents.none { it.consentType == "PD" }) {
            throw IllegalStateException("Необходимо подписать согласия БКИ и ПДн перед отправкой")
        }

        application.status = ApplicationStatus.ENRICHMENT
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        sseService.sendUpdate(applicationId.toString(), "ENRICHMENT", "Заявка отправлена, собираем данные")
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
    fun getPendingApplicationsForManager(page: Int, size: Int): PageResponse<ManagerPendingApplicationResponse> {
        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "updatedAt"))
        val applications = applicationRepository.findByStatus(ApplicationStatus.MANUAL_REVIEW, pageable)
        return PageResponse.from(applications.map {
            val factors = scoringDetailsCodec.decode(it.scoringDetails)
            val negativeFactors = factors.filter { factor -> factor.points < 0 }.sortedBy { factor -> factor.points }
            val reason = negativeFactors.take(2).joinToString("; ") { factor -> factor.label }
                .ifBlank { it.rejectionReason ?: "Ручная проверка" }

            ManagerPendingApplicationResponse(
                id = it.id!!,
                company = it.company.toSummary(),
                amount = it.requestedAmount,
                score = it.riskScore,
                grade = it.riskScore?.let { score -> gradeResolver.gradeFor(score) },
                reason = reason,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
            )
        })
    }

    @Transactional(readOnly = true)
    fun getExplanation(applicationId: UUID): ExplanationResponse {
        val application = applicationRepository.findById(applicationId).orElseThrow {
            NotFoundException("Application not found")
        }
        accessService.checkAccess(application)

        val factors = scoringDetailsCodec.decode(application.scoringDetails)
        val reasons = factors
            .filter { it.points < 0 }
            .sortedBy { it.points }
            .take(3)
            .map { formatFactor(it) }
            .ifEmpty { factors.take(3).map { formatFactor(it) } }
            .ifEmpty { listOf("Стандартный риск") }

        val nextSteps = buildNextSteps(application, factors)
        val fallback = buildFallbackExplanation(application, reasons)
        val factsForAi = buildString {
            append("Статус заявки: ${application.status}. ")
            append("Балл: ${application.riskScore ?: "не рассчитан"}. ")
            application.riskScore?.let { append("Грейд: ${gradeResolver.gradeFor(it)}. ") }
            append("Запрошенная сумма: ${application.requestedAmount.toPlainString()} ₽. ")
            application.approvedAmount?.let { append("Одобренная сумма: ${it.toPlainString()} ₽. ") }
            append("Главные причины: ${reasons.joinToString(", ")}. ")
            append("Следующие шаги: ${nextSteps.joinToString(", ")}.")
        }

        val prompt = "Ты вежливый банковский ассистент. На основе данных напиши клиенту краткое объяснение решения " +
                "(1-2 предложения). Не используй сложные банковские термины и не добавляй числа, которых нет в данных. " +
                "Данные: $factsForAi"

        val aiSummary = aiAssistantClient.generateExplanation(prompt, fallback)

        return ExplanationResponse(
            summary = aiSummary,
            reasons = reasons,
            nextSteps = nextSteps
        )
    }

    @Transactional(readOnly = true)
    fun getPreapprovedOffer(inn: String): PreapprovedOfferResponse {
        val company = companyRepository.findByInn(inn)
            ?: throw NotFoundException("Company not found")

        val maxAmount = company.annualRevenue
            .divide(BigDecimal("4"))
            .min(BigDecimal("30000000"))
            .max(BigDecimal("500000"))
        val termMonths = 24
        val rate = 22.0

        return PreapprovedOfferResponse(
            inn = inn,
            maxAmount = maxAmount,
            termMonths = termMonths,
            interestRate = rate,
            monthlyPayment = OfferMath.monthlyPayment(maxAmount, termMonths, rate),
            overpayment = OfferMath.overpayment(maxAmount, termMonths, rate),
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

        val factors = scoringDetailsCodec.decode(application.scoringDetails).map {
            ScoreFactorDto(
                code = it.code,
                name = it.label,
                contribution = it.points,
                description = it.description
            )
        }

        val grade = gradeResolver.gradeFor(application.riskScore!!)

        return ScoreResponse(
            score = application.riskScore!!,
            grade = grade,
            riskLevel = gradeResolver.riskLevel(grade),
            factors = factors
        )
    }

    private fun formatFactor(factor: ScoringFactorRecord): String =
        if (factor.description.isBlank()) factor.label else "${factor.label} (${factor.description})"

    private fun buildNextSteps(application: Application, factors: List<ScoringFactorRecord>): List<String> {
        val codes = factors.mapNotNull { it.code }.toSet()
        val steps = mutableListOf<String>()

        if ("R01" in codes || "R05" in codes) {
            steps += "Сократите долговую нагрузку: погасите часть действующих кредитов."
        }
        if ("R02" in codes) {
            steps += "Закройте просроченную задолженность — это самый важный фактор для банка."
        }
        if ("R03" in codes) {
            steps += "Кредитная история ещё короткая. Вернитесь через 3 месяца: лимит пересчитается автоматически."
        }
        if ("R04" in codes) {
            steps += "Погасите налоговую задолженность и приложите подтверждающий документ."
        }
        if ("R06" in codes) {
            steps += "Запросите сумму меньше 50% годовой выручки или увеличьте подтверждённую выручку."
        }

        when (application.status) {
            ApplicationStatus.REJECTED ->
                steps += "Подайте заявку повторно через 3 месяца или обратитесь к менеджеру."
            ApplicationStatus.COUNTER_OFFER ->
                steps += "Примите одно из контрпредложений: лимит, поручительство или залог."
            ApplicationStatus.MANUAL_REVIEW ->
                steps += "Менеджер проверит данные в течение 24 часов. Дополнительно ничего делать не нужно."
            ApplicationStatus.NEEDS_MORE_INFO ->
                steps += "Загрузите запрошенные документы в карточке заявки."
            ApplicationStatus.APPROVED ->
                steps += "Примите предложение и подпишите документы."
            ApplicationStatus.SIGNING ->
                steps += "Подпишите документы, чтобы получить деньги на счёт."
            ApplicationStatus.FUNDED ->
                steps += "Деньги зачислены на счёт. Следите за графиком платежей."
            else -> Unit
        }

        return steps.distinct().take(4).ifEmpty {
            listOf("Следите за статусом заявки — он обновляется автоматически.")
        }
    }

    private fun buildFallbackExplanation(application: Application, reasons: List<String>): String {
        val scorePart = application.riskScore?.let { " Балл: $it." }.orEmpty()
        return when (application.status) {
            ApplicationStatus.APPROVED ->
                "Заявка одобрена.$scorePart Условия предложения уже доступны в карточке заявки."
            ApplicationStatus.COUNTER_OFFER ->
                "Запрошенная сумма больше доступного лимита.$scorePart Мы подготовили альтернативные условия — выберите подходящий вариант."
            ApplicationStatus.REJECTED ->
                "К сожалению, мы не можем одобрить заявку.$scorePart Основная причина: ${reasons.first()}."
            ApplicationStatus.MANUAL_REVIEW ->
                "Заявка передана менеджеру на ручную проверку.$scorePart Это займёт до 24 часов."
            ApplicationStatus.NEEDS_MORE_INFO ->
                "Для продолжения рассмотрения нужны дополнительные документы.$scorePart Загрузите их в карточке заявки."
            else ->
                "Мы проанализировали заявку на основе доступных данных.$scorePart Рекомендуем ознакомиться с шагами для улучшения кредитного профиля."
        }
    }

    private fun mapToResponse(application: Application): ApplicationResponse {
        return ApplicationResponse(
            id = application.id!!,
            status = application.status,
            company = application.company.toSummary(),
            product = application.product.toSummary(),
            requestedAmount = application.requestedAmount,
            requestedTermMonths = application.requestedTermMonths,
            purpose = application.purpose,
            hasCollateral = application.hasCollateral,
            riskScore = application.riskScore,
            scoringDetails = application.scoringDetails,
            approvedAmount = application.approvedAmount,
            approvedTermMonths = application.approvedTermMonths,
            rejectionReason = application.rejectionReason,
            createdAt = application.createdAt,
            updatedAt = application.updatedAt
        )
    }

    private fun Company.toSummary(): CompanySummary = CompanySummary(
        id = id!!,
        inn = inn,
        ogrn = ogrn,
        name = name,
        category = category,
        status = status
    )

    private fun CreditProduct.toSummary(): ProductSummary = ProductSummary(
        id = id!!,
        name = name,
        baseRate = baseRate
    )
}
