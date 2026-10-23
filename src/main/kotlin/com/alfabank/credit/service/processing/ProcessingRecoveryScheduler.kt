package com.alfabank.credit.service.processing

import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.ProcessingJobStatus
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.ProcessingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ProcessingRecoveryScheduler(
    private val applicationRepository: ApplicationRepository,
    private val processingJobRepository: ProcessingJobRepository,
    private val processingJobService: ProcessingJobService,
    private val applicationProcessingService: ApplicationProcessingService,
    @Value("\${app.processing.batch-size:20}") private val batchSize: Int,
    @Value("\${app.processing.stuck-after-seconds:120}") private val stuckAfterSeconds: Long
) {
    private val log = LoggerFactory.getLogger(ProcessingRecoveryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${app.processing.retry-interval-ms:30000}")
    fun recover() {
        retryDueJobs()
        enqueueStuckApplications()
    }

    private fun retryDueJobs() {
        val pageable = PageRequest.of(0, batchSize)
        val jobs = processingJobRepository.findDue(
            listOf(ProcessingJobStatus.PENDING, ProcessingJobStatus.RETRY),
            Instant.now(),
            pageable
        )

        jobs.forEach { job ->
            try {
                applicationProcessingService.process(job.applicationId)
                processingJobService.markDone(job.applicationId)
            } catch (e: Exception) {
                log.error("Не удалось повторить обработку заявки {}", job.applicationId, e)
                processingJobService.registerFailure(job.applicationId, e)
            }
        }
    }

    private fun enqueueStuckApplications() {
        val cutoff = Instant.now().minusSeconds(stuckAfterSeconds)
        val pageable = PageRequest.of(0, batchSize)
        val stuckApplications = applicationRepository.findByStatusInAndUpdatedAtBefore(
            listOf(
                ApplicationStatus.SUBMITTED,
                ApplicationStatus.ENRICHMENT,
                ApplicationStatus.SCORING,
                ApplicationStatus.DECISION
            ),
            cutoff,
            pageable
        )

        stuckApplications.forEach { application ->
            val applicationId = application.id ?: return@forEach
            if (processingJobRepository.findByApplicationId(applicationId) == null) {
                processingJobService.ensureJob(applicationId)
                log.warn(
                    "Заявка {} зависла в статусе {}, поставлена в очередь восстановления",
                    applicationId,
                    application.status
                )
            }
        }
    }
}
