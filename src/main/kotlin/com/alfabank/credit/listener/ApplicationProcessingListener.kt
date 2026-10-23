package com.alfabank.credit.listener

import com.alfabank.credit.domain.ApplicationSubmittedEvent
import com.alfabank.credit.service.processing.ApplicationProcessingService
import com.alfabank.credit.service.processing.ProcessingJobService
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class ApplicationProcessingListener(
    private val applicationProcessingService: ApplicationProcessingService,
    private val processingJobService: ProcessingJobService
) {
    private val log = LoggerFactory.getLogger(ApplicationProcessingListener::class.java)

    @ApplicationModuleListener
    fun handleApplicationSubmitted(event: ApplicationSubmittedEvent) {
        log.info("Начинаем обработку заявки: {}", event.applicationId)
        try {
            applicationProcessingService.process(event.applicationId)
            processingJobService.markDone(event.applicationId)
        } catch (e: Exception) {
            log.error("Ошибка обработки заявки {}", event.applicationId, e)
            processingJobService.registerFailure(event.applicationId, e)
        }
    }
}
