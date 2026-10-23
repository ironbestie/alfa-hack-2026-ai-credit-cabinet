package com.alfabank.credit.repository

import com.alfabank.credit.domain.ProcessingJob
import com.alfabank.credit.domain.ProcessingJobStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ProcessingJobRepository : JpaRepository<ProcessingJob, UUID> {
    fun findByApplicationId(applicationId: UUID): ProcessingJob?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProcessingJob j where j.applicationId = :applicationId")
    fun findByApplicationIdForUpdate(@Param("applicationId") applicationId: UUID): ProcessingJob?

    @Query(
        "select j from ProcessingJob j " +
            "where j.status in :statuses and j.nextAttemptAt <= :now " +
            "order by j.nextAttemptAt asc"
    )
    fun findDue(
        @Param("statuses") statuses: Collection<ProcessingJobStatus>,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<ProcessingJob>

    fun findByStatus(status: ProcessingJobStatus, pageable: Pageable): Page<ProcessingJob>
}
