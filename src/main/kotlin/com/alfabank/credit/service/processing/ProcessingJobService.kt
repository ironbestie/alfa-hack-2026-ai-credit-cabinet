package com.alfabank.credit.service.processing

import com.alfabank.credit.domain.ProcessingJob
import com.alfabank.credit.domain.ProcessingJobStatus
import com.alfabank.credit.dto.DeadLetterResponse
import com.alfabank.credit.dto.PageResponse
import com.alfabank.credit.exception.NotFoundException
import com.alfabank.credit.repository.ProcessingJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ProcessingJobService(
    private val processingJobRepository: ProcessingJobRepository,
    @Value("\${app.processing.max-attempts:5}") private val maxAttempts: Int,
    @Value("\${app.processing.retry-base-seconds:30}") private val retryBaseSeconds: Long,
    @Value("\${app.processing.retry-max-seconds:3600}") private val retryMaxSeconds: Long
) {
    private val log = LoggerFactory.getLogger(ProcessingJobService::class.java)

    @Transactional
    fun ensureJob(applicationId: UUID): ProcessingJob {
        processingJobRepository.findByApplicationIdForUpdate(applicationId)?.let { return it }
        return processingJobRepository.save(
            ProcessingJob(
                applicationId = applicationId,
                maxAttempts = maxAttempts
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun registerFailure(applicationId: UUID, error: Throwable) {
        val job = processingJobRepository.findByApplicationIdForUpdate(applicationId)
            ?: ProcessingJob(applicationId = applicationId, maxAttempts = maxAttempts)

        if (job.status == ProcessingJobStatus.DONE) {
            job.attempts = 0
        }
        job.attempts += 1
        job.lastError = (error.message ?: error::class.simpleName ?: "Unknown error").take(4000)
        job.updatedAt = Instant.now()

        if (job.attempts >= job.maxAttempts) {
            job.status = ProcessingJobStatus.DEAD_LETTER
            job.nextAttemptAt = Instant.now()
            log.error(
                "Заявка {} помещена в DLQ после {} попыток: {}",
                applicationId,
                job.attempts,
                job.lastError
            )
        } else {
            val delaySeconds = retryDelaySeconds(job.attempts)
            job.status = ProcessingJobStatus.RETRY
            job.nextAttemptAt = Instant.now().plusSeconds(delaySeconds)
            log.warn(
                "Ошибка обработки заявки {}, попытка {}/{}, повтор через {} c: {}",
                applicationId,
                job.attempts,
                job.maxAttempts,
                delaySeconds,
                job.lastError
            )
        }

        processingJobRepository.save(job)
    }

    @Transactional
    fun markDone(applicationId: UUID) {
        val job = processingJobRepository.findByApplicationIdForUpdate(applicationId) ?: return
        job.status = ProcessingJobStatus.DONE
        job.lastError = null
        job.updatedAt = Instant.now()
        processingJobRepository.save(job)
    }

    @Transactional
    fun replay(applicationId: UUID) {
        val job = processingJobRepository.findByApplicationIdForUpdate(applicationId)
            ?: throw NotFoundException("Задача обработки не найдена")
        if (job.status != ProcessingJobStatus.DEAD_LETTER) {
            throw IllegalStateException("Задача обработки не находится в DLQ")
        }
        job.status = ProcessingJobStatus.RETRY
        job.attempts = 0
        job.lastError = null
        job.nextAttemptAt = Instant.now()
        job.updatedAt = Instant.now()
        processingJobRepository.save(job)
        log.info("Заявка {} возвращена из DLQ в очередь обработки", applicationId)
    }

    @Transactional(readOnly = true)
    fun getDeadLetters(page: Int, size: Int): PageResponse<DeadLetterResponse> {
        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        val jobs = processingJobRepository.findByStatus(ProcessingJobStatus.DEAD_LETTER, pageable)
        return PageResponse.from(
            jobs.map {
                DeadLetterResponse(
                    applicationId = it.applicationId,
                    attempts = it.attempts,
                    lastError = it.lastError,
                    updatedAt = it.updatedAt
                )
            }
        )
    }

    private fun retryDelaySeconds(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 20)
        val multiplier = 1L shl exponent
        return (retryBaseSeconds * multiplier).coerceAtMost(retryMaxSeconds)
    }
}
