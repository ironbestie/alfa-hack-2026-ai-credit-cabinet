package com.alfabank.credit.listener

import com.alfabank.credit.domain.*
import com.alfabank.credit.integration.BkiClient
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
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
    private val bkiClient: BkiClient
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingListener::class.java)

    @ApplicationModuleListener
    fun handleApplicationSubmitted(event: ApplicationSubmittedEvent) {
        log.info(">>> Начинаем обработку заявки: ${event.applicationId}")

        val application = applicationRepository.findById(event.applicationId).orElse(null) ?: return

        // ЭТАП 1: ENRICHING
        application.status = ApplicationStatus.ENRICHING
        application.updatedAt = Instant.now()
        applicationRepository.save(application)
        saveEvent(application, ApplicationEventType.DATA_ENRICHED, "Запущен сбор данных из БКИ и ЕГРЮЛ")

        val bkiData = bkiClient.fetchCreditHistory(application.company.inn)
        log.info(">>> Данные БКИ для ${application.company.inn}: Просрочек - ${bkiData.overdueCount}")

        Thread.sleep(2000)

        application.status = ApplicationStatus.SCORING
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val scoringResult = scoringService.calculateScore(application)
        application.riskScore = scoringResult.score
        application.scoringDetails = scoringResult.details
        applicationRepository.save(application)

        saveEvent(application, ApplicationEventType.SCORING_COMPLETED, "Скоринг завершен. Балл: ${scoringResult.score}. Детали: ${scoringResult.details}")

        Thread.sleep(2000)

        val decisionResult = decisionService.makeDecision(application, scoringResult)

        application.status = decisionResult.status
        application.approvedAmount = decisionResult.approvedAmount
        application.rejectionReason = decisionResult.rejectionReason
        application.updatedAt = Instant.now()
        applicationRepository.save(application)

        val decisionMessage = if (decisionResult.rejectionReason == null) {
            "Кредит одобрен на сумму ${decisionResult.approvedAmount} ₽"
        } else {
            decisionResult.rejectionReason
        }

        saveEvent(application, ApplicationEventType.DECISION_MADE, decisionResult.rejectionReason ?: decisionMessage)

        log.info(">>> Обработка заявки завершена: ${event.applicationId}")
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