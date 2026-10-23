package com.alfabank.credit.service.processing

import com.alfabank.credit.domain.ProcessingJob
import com.alfabank.credit.domain.ProcessingJobStatus
import com.alfabank.credit.repository.ProcessingJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class ProcessingJobServiceTest {

    private val repository = mock(ProcessingJobRepository::class.java)
    private val service = ProcessingJobService(
        processingJobRepository = repository,
        maxAttempts = 3,
        retryBaseSeconds = 10,
        retryMaxSeconds = 60
    )

    @Test
    fun `первая ошибка переводит задачу в retry с задержкой`() {
        val applicationId = UUID.randomUUID()
        val job = ProcessingJob(applicationId = applicationId, maxAttempts = 3)
        `when`(repository.findByApplicationIdForUpdate(applicationId)).thenReturn(job)
        `when`(repository.save(any(ProcessingJob::class.java))).thenAnswer { it.getArgument(0) }

        service.registerFailure(applicationId, RuntimeException("БКИ недоступно"))

        assertEquals(ProcessingJobStatus.RETRY, job.status)
        assertEquals(1, job.attempts)
        assertEquals("БКИ недоступно", job.lastError)
        assertTrue(job.nextAttemptAt.isAfter(Instant.now().plusSeconds(5)))
    }

    @Test
    fun `после исчерпания попыток задача попадает в DLQ`() {
        val applicationId = UUID.randomUUID()
        val job = ProcessingJob(applicationId = applicationId, maxAttempts = 3)
        `when`(repository.findByApplicationIdForUpdate(applicationId)).thenReturn(job)
        `when`(repository.save(any(ProcessingJob::class.java))).thenAnswer { it.getArgument(0) }

        repeat(3) {
            service.registerFailure(applicationId, RuntimeException("Ошибка"))
        }

        assertEquals(ProcessingJobStatus.DEAD_LETTER, job.status)
        assertEquals(3, job.attempts)
    }

    @Test
    fun `повтор из DLQ сбрасывает счётчик попыток`() {
        val applicationId = UUID.randomUUID()
        val job = ProcessingJob(
            applicationId = applicationId,
            status = ProcessingJobStatus.DEAD_LETTER,
            attempts = 3,
            maxAttempts = 3,
            lastError = "Ошибка"
        )
        `when`(repository.findByApplicationIdForUpdate(applicationId)).thenReturn(job)
        `when`(repository.save(any(ProcessingJob::class.java))).thenAnswer { it.getArgument(0) }

        service.replay(applicationId)

        assertEquals(ProcessingJobStatus.RETRY, job.status)
        assertEquals(0, job.attempts)
        assertNull(job.lastError)
    }

    @Test
    fun `успешная обработка помечает задачу выполненной`() {
        val applicationId = UUID.randomUUID()
        val job = ProcessingJob(
            applicationId = applicationId,
            status = ProcessingJobStatus.RETRY,
            attempts = 2,
            lastError = "Ошибка"
        )
        `when`(repository.findByApplicationIdForUpdate(applicationId)).thenReturn(job)
        `when`(repository.save(any(ProcessingJob::class.java))).thenAnswer { it.getArgument(0) }

        service.markDone(applicationId)

        assertEquals(ProcessingJobStatus.DONE, job.status)
        assertNull(job.lastError)
    }
}
