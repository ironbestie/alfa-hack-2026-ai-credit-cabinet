package com.alfabank.credit.listener

import com.alfabank.credit.domain.*
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.OfferRepository
import com.alfabank.credit.service.SseService
import com.alfabank.credit.service.scoring.DecisionService
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
    private val sseService: SseService
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingListener::class.java)

    @ApplicationModuleListener
    fun handleApplicationSubmitted(event: ApplicationSubmittedEvent) {
        log.info("Начинаем обработку заявки: ${event.applicationId}")
        val application = applicationRepository.findById(event.applicationId).orElse(null) ?: return

        updateStatus(application, ApplicationStatus.ENRICHMENT, "ENRICHMENT", "Сбор данных", ApplicationEventType.DATA_ENRICHED)
        updateStatus(application, ApplicationStatus.SCORING, "SCORING", "Оценка риска", ApplicationEventType.STATUS_CHANGED)

        val payload = scoringService.calculateScore(application)
        application.riskScore = payload.result.score
        application.scoringDetails = payload.result.factors.joinToString("; ") { "${it.name} (${it.contribution})" }
        applicationRepository.save(application)

        val decisionOutcome = decisionService.makeDecision(application, payload)

        application.status = ApplicationStatus.valueOf(decisionOutcome.status)
        application.updatedAt = Instant.now()

        val mainOffer = decisionOutcome.offers.firstOrNull()
        if (mainOffer != null) {
            application.approvedAmount = mainOffer.amount
            application.approvedTermMonths = mainOffer.termMonths
        }
        if (decisionOutcome.status == "REJECTED") {
            application.rejectionReason = decisionOutcome.reason
        }
        applicationRepository.save(application)

        decisionOutcome.offers.forEach { offer ->
            offerRepository.save(Offer(
                application = application,
                amount = offer.amount,
                termMonths = offer.termMonths,
                interestRate = offer.ratePercent.toDouble(), // <-- ИСПРАВЛЕНО
                type = offer.kind
            ))
        }

        val decisionMessage = when (decisionOutcome.status) {
            "APPROVED" -> "Кредит одобрен"
            "COUNTER_OFFER" -> "Сформировано контрпредложение"
            "MANUAL_REVIEW" -> "Заявка отправлена на ручное рассмотрение"
            else -> decisionOutcome.reason
        }

        sseService.sendUpdate(application.id.toString(), decisionOutcome.status, decisionMessage)
        saveEvent(application, ApplicationEventType.DECISION_MADE, decisionMessage)
        log.info("Обработка завершена: ${event.applicationId}")
    }

    private fun updateStatus(application: Application, status: ApplicationStatus, sseEventName: String, message: String, eventType: ApplicationEventType) {
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