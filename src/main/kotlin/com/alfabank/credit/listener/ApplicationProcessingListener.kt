package com.alfabank.credit.listener

import com.alfabank.credit.domain.*
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.OfferRepository
import com.alfabank.credit.service.SseService
import com.alfabank.credit.service.scoring.DecisionService
import com.alfabank.credit.service.scoring.ScoringDetailsCodec
import com.alfabank.credit.service.scoring.ScoringPayload
import com.alfabank.credit.service.scoring.ScoringService
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ApplicationProcessingListener(
    private val applicationRepository: ApplicationRepository,
    private val eventRepository: ApplicationEventRepository,
    private val offerRepository: OfferRepository,
    private val scoringService: ScoringService,
    private val decisionService: DecisionService,
    private val scoringDetailsCodec: ScoringDetailsCodec,
    private val sseService: SseService
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingListener::class.java)

    @ApplicationModuleListener
    fun handleApplicationSubmitted(event: ApplicationSubmittedEvent) {
        log.info("Начинаем обработку заявки: ${event.applicationId}")
        val application = applicationRepository.findById(event.applicationId).orElse(null) ?: return

        // Старые заявки могли остаться в SUBMITTED. Новые сразу создаются в ENRICHMENT.
        if (application.status == ApplicationStatus.SUBMITTED) {
            transition(application, ApplicationStatus.ENRICHMENT, "ENRICHMENT", "Сбор данных", ApplicationEventType.DATA_ENRICHED)
        }
        if (application.status != ApplicationStatus.ENRICHMENT) {
            log.warn("Заявка ${event.applicationId} уже не в ENRICHMENT (${application.status}), пропускаем")
            return
        }

        // ENRICHMENT: собираем данные из моков. БКИ вызывается только при наличии согласия.
        val enrichment = scoringService.enrich(application)
        saveEvent(application, ApplicationEventType.DATA_ENRICHED, "Данные из ЕГРЮЛ, ФНС, БКИ и банка собраны")

        // Стоп-факторы проверяем до скоринга и до проверки полноты источников:
        // ликвидация компании должна давать отказ, даже если БКИ/ФНС не ответили.
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

        // SCORING
        transition(application, ApplicationStatus.SCORING, "SCORING", "Оценка риска", ApplicationEventType.STATUS_CHANGED)
        val result = scoringService.score(enrichment.input)
        application.riskScore = result.score
        application.scoringDetails = scoringDetailsCodec.encode(result.factors)
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        saveEvent(
            application,
            ApplicationEventType.SCORING_COMPLETED,
            "Скоринг завершён: балл ${result.score}, грейд ${result.grade}"
        )
        sseService.sendUpdate(application.id.toString(), "SCORING_COMPLETED", "Балл ${result.score}, грейд ${result.grade}")

        // DECISION
        transition(application, ApplicationStatus.DECISION, "DECISION", "Принятие решения", ApplicationEventType.STATUS_CHANGED)
        val decisionOutcome = decisionService.makeDecision(
            application,
            ScoringPayload(enrichment.input, result, enrichment.sourcesOk)
        )

        application.status = ApplicationStatus.valueOf(decisionOutcome.status)
        application.updatedAt = Instant.now()

        val mainOffer = decisionOutcome.offers.firstOrNull()
        if (mainOffer != null) {
            application.approvedAmount = mainOffer.amount
            application.approvedTermMonths = mainOffer.termMonths
        }
        if (decisionOutcome.status == "REJECTED" || decisionOutcome.status == "MANUAL_REVIEW") {
            application.rejectionReason = decisionOutcome.reason
        }
        applicationRepository.save(application)

        decisionOutcome.offers.forEach { offer ->
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

        val decisionMessage = when (decisionOutcome.status) {
            "APPROVED" -> "Кредит одобрен"
            "COUNTER_OFFER" -> "Сформировано контрпредложение"
            "MANUAL_REVIEW" -> "Заявка отправлена на ручное рассмотрение"
            else -> decisionOutcome.reason
        }

        sseService.sendUpdate(application.id.toString(), decisionOutcome.status, decisionMessage)
        saveEvent(application, ApplicationEventType.DECISION_MADE, decisionMessage)
        log.info("Обработка завершена: ${event.applicationId}, статус ${application.status}")
    }

    private fun transition(
        application: Application,
        status: ApplicationStatus,
        sseEventName: String,
        message: String,
        eventType: ApplicationEventType
    ) {
        ApplicationStateMachine.validateTransition(application.status, status)
        application.status = status
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        sseService.sendUpdate(application.id.toString(), sseEventName, message)
        saveEvent(application, eventType, message)
    }

    private fun saveEvent(application: Application, type: ApplicationEventType, description: String) {
        eventRepository.save(ApplicationEvent(application = application, eventType = type, description = description))
    }
}
