package com.alfabank.credit.service.processing

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEvent
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStateMachine
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Offer
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.OfferRepository
import com.alfabank.credit.scoring.DecisionOutcome
import com.alfabank.credit.scoring.GradeResolver
import com.alfabank.credit.scoring.ScoreFactor
import com.alfabank.credit.scoring.TeamScoringResult
import com.alfabank.credit.service.SseService
import com.alfabank.credit.service.scoring.DecisionService
import com.alfabank.credit.service.scoring.ScoringDetailsCodec
import com.alfabank.credit.service.scoring.ScoringPayload
import com.alfabank.credit.service.scoring.ScoringService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class ApplicationProcessingService(
    private val applicationRepository: ApplicationRepository,
    private val eventRepository: ApplicationEventRepository,
    private val offerRepository: OfferRepository,
    private val scoringService: ScoringService,
    private val decisionService: DecisionService,
    private val scoringDetailsCodec: ScoringDetailsCodec,
    private val gradeResolver: GradeResolver,
    private val sseService: SseService,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingService::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun process(applicationId: UUID) {
        val application = applicationRepository.findWithDetailsById(applicationId) ?: return

        if (application.status == ApplicationStatus.SUBMITTED) {
            transition(
                application,
                ApplicationStatus.ENRICHMENT,
                "ENRICHMENT",
                "Сбор данных",
                ApplicationEventType.STATUS_CHANGED
            )
        }

        if (application.status !in setOf(
                ApplicationStatus.ENRICHMENT,
                ApplicationStatus.SCORING,
                ApplicationStatus.DECISION
            )
        ) {
            log.info("Заявка {} не требует обработки: {}", applicationId, application.status)
            return
        }

        val enrichment = scoringService.enrich(application)
        if (application.status == ApplicationStatus.ENRICHMENT) {
            saveEvent(application, ApplicationEventType.DATA_ENRICHED, "Данные из ЕГРЮЛ, ФНС, БКИ и банка собраны")
        }

        scoringService.stopFactor(enrichment.input)?.let { reason ->
            application.rejectionReason = reason
            transition(
                application,
                ApplicationStatus.REJECTED,
                "REJECTED",
                "Стоп-фактор: $reason",
                ApplicationEventType.DECISION_MADE
            )
            return
        }

        if (!enrichment.sourcesOk) {
            application.rejectionReason = "Не все источники данных ответили"
            transition(
                application,
                ApplicationStatus.MANUAL_REVIEW,
                "MANUAL_REVIEW",
                "Источник данных не ответил, заявка отправлена на ручную проверку",
                ApplicationEventType.STATUS_CHANGED
            )
            return
        }

        if (application.status == ApplicationStatus.ENRICHMENT) {
            transition(
                application,
                ApplicationStatus.SCORING,
                "SCORING",
                "Оценка риска",
                ApplicationEventType.STATUS_CHANGED
            )
        }

        val result = application.riskScore?.let { persistedResult(application) } ?: run {
            val scored = scoringService.score(enrichment.input)
            saveScoring(application, scored)
            scored
        }

        if (application.status == ApplicationStatus.SCORING) {
            transition(
                application,
                ApplicationStatus.DECISION,
                "DECISION",
                "Принятие решения",
                ApplicationEventType.STATUS_CHANGED
            )
        }

        if (application.status == ApplicationStatus.DECISION) {
            val outcome = decisionService.makeDecision(
                application,
                ScoringPayload(enrichment.input, result, enrichment.sourcesOk)
            )
            applyDecision(application, outcome)
        }
    }

    private fun persistedResult(application: Application): TeamScoringResult {
        val score = application.riskScore ?: error("Scoring result is missing")
        val factors = scoringDetailsCodec.decode(application.scoringDetails).map {
            ScoreFactor(
                name = it.label,
                points = it.points,
                description = it.description,
                reasonCode = it.code,
                contribution = it.points
            )
        }
        return TeamScoringResult(score, gradeResolver.gradeFor(score), factors)
    }

    private fun saveScoring(application: Application, result: TeamScoringResult) {
        val applicationId = application.id!!
        transactionTemplate.executeWithoutResult {
            application.riskScore = result.score
            application.scoringDetails = scoringDetailsCodec.encode(result.factors)
            application.updatedAt = Instant.now()
            applicationRepository.save(application)
            eventRepository.save(
                ApplicationEvent(
                    application = application,
                    eventType = ApplicationEventType.SCORING_COMPLETED,
                    description = "Скоринг завершён: балл ${result.score}, грейд ${result.grade}"
                )
            )
        }
        sseService.sendUpdate(
            applicationId.toString(),
            "SCORING_COMPLETED",
            "Балл ${result.score}, грейд ${result.grade}"
        )
    }

    private fun applyDecision(application: Application, outcome: DecisionOutcome) {
        val applicationId = application.id!!
        val nextStatus = ApplicationStatus.valueOf(outcome.status)
        ApplicationStateMachine.validateTransition(application.status, nextStatus)
        val mainOffer = outcome.offers.firstOrNull()
        val message = decisionMessage(outcome)

        transactionTemplate.executeWithoutResult {
            application.status = nextStatus
            application.updatedAt = Instant.now()

            if (mainOffer != null) {
                application.approvedAmount = mainOffer.amount
                application.approvedTermMonths = mainOffer.termMonths
            }
            if (nextStatus == ApplicationStatus.REJECTED || nextStatus == ApplicationStatus.MANUAL_REVIEW) {
                application.approvedAmount = null
                application.approvedTermMonths = null
                application.rejectionReason = outcome.reason
            } else {
                application.rejectionReason = null
            }
            applicationRepository.save(application)

            offerRepository.deleteAll(offerRepository.findByApplicationId(applicationId))
            outcome.offers.forEach { offer ->
                offerRepository.save(
                    Offer(
                        application = application,
                        amount = offer.amount,
                        termMonths = offer.termMonths,
                        interestRate = offer.ratePercent.toDouble(),
                        type = offer.kind
                    )
                )
            }

            eventRepository.save(
                ApplicationEvent(
                    application = application,
                    eventType = ApplicationEventType.DECISION_MADE,
                    description = message
                )
            )
        }

        sseService.sendUpdate(applicationId.toString(), outcome.status, message)
        log.info("Обработка завершена: {}, статус {}", applicationId, nextStatus)
    }

    private fun decisionMessage(outcome: DecisionOutcome): String = when (outcome.status) {
        "APPROVED" -> "Кредит одобрен"
        "COUNTER_OFFER" -> "Сформировано контрпредложение"
        "MANUAL_REVIEW" -> "Заявка отправлена на ручное рассмотрение"
        else -> outcome.reason
    }

    private fun transition(
        application: Application,
        status: ApplicationStatus,
        sseEventName: String,
        message: String,
        eventType: ApplicationEventType
    ) {
        ApplicationStateMachine.validateTransition(application.status, status)
        val applicationId = application.id!!

        transactionTemplate.executeWithoutResult {
            application.status = status
            application.updatedAt = Instant.now()
            applicationRepository.save(application)
            eventRepository.save(
                ApplicationEvent(
                    application = application,
                    eventType = eventType,
                    description = message
                )
            )
        }

        sseService.sendUpdate(applicationId.toString(), sseEventName, message)
    }

    private fun saveEvent(application: Application, type: ApplicationEventType, description: String) {
        eventRepository.save(
            ApplicationEvent(
                application = application,
                eventType = type,
                description = description
            )
        )
    }
}
