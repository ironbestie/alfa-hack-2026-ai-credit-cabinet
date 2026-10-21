package com.alfabank.credit.listener

import com.alfabank.credit.domain.*
import com.alfabank.credit.integration.BkiClient
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
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
    private val scoringService: ScoringService,
    private val decisionService: DecisionService,
    private val bkiClient: BkiClient,
    private val sseService: SseService
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingListener::class.java)

    @ApplicationModuleListener
    fun handleApplicationSubmitted(event: ApplicationSubmittedEvent) {
        log.info("Начинаем обработку заявки: ${event.applicationId}")

        val application = applicationRepository.findById(event.applicationId).orElse(null) ?: return

        updateStatus(
            application,
            ApplicationStatus.ENRICHMENT,
            "ENRICHMENT",
            "Запущен сбор данных из БКИ и ЕГРЮЛ",
            ApplicationEventType.DATA_ENRICHED
        )

        val bkiData = bkiClient.fetchCreditHistory(application.company.inn)

        if (bkiData.rating == "UNKNOWN") {
            updateStatus(
                application,
                ApplicationStatus.MANUAL_REVIEW,
                "MANUAL_REVIEW",
                "Сервис БКИ недоступен. Требуется ручная проверка.",
                ApplicationEventType.STATUS_CHANGED
            )
            log.warn("БКИ недоступен. Заявка ${event.applicationId} отправлена в MANUAL_REVIEW.")
            return
        }

        // ЭТАП 2: SCORING
        updateStatus(
            application,
            ApplicationStatus.SCORING,
            "SCORING",
            "Оценка риска",
            ApplicationEventType.STATUS_CHANGED
        )

        val scoringResult = scoringService.calculateScore(application)
        application.riskScore = scoringResult.score
        application.scoringDetails = scoringResult.details
        applicationRepository.save(application)

        saveEvent(application, ApplicationEventType.SCORING_COMPLETED, "Скоринг завершен. Балл: ${scoringResult.score} (Грейд: ${scoringResult.grade})")

        val decisionResult = decisionService.makeDecision(application, scoringResult)

        application.approvedAmount = decisionResult.approvedAmount
        application.approvedTermMonths = decisionResult.approvedTermMonths
        application.rejectionReason = decisionResult.rejectionReason

        val decisionMessage = when (decisionResult.status) {
            ApplicationStatus.APPROVED -> "Кредит одобрен на сумму ${decisionResult.approvedAmount} ₽"
            ApplicationStatus.COUNTER_OFFER -> "Сформировано контрпредложение на сумму ${decisionResult.approvedAmount} ₽"
            ApplicationStatus.MANUAL_REVIEW -> "Заявка отправлена на ручное рассмотрение"
            else -> decisionResult.rejectionReason ?: "Отказ"
        }

        updateStatus(
            application,
            decisionResult.status,
            decisionResult.status.name,
            decisionMessage,
            ApplicationEventType.DECISION_MADE
        )

        log.info("Обработка заявки завершена: ${event.applicationId}")
    }

    private fun updateStatus(
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
        val appEvent = ApplicationEvent(
            application = application,
            eventType = type,
            description = description
        )
        eventRepository.save(appEvent)
    }
}