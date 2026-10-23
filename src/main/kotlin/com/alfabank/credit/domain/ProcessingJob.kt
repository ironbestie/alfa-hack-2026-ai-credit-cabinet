package com.alfabank.credit.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

enum class ProcessingJobStatus {
    PENDING,
    RETRY,
    DONE,
    DEAD_LETTER
}

@Entity
@Table(
    name = "processing_jobs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_processing_jobs_application", columnNames = ["application_id"])
    ]
)
class ProcessingJob(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "application_id", nullable = false)
    val applicationId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProcessingJobStatus = ProcessingJobStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 5,

    @Column(name = "last_error", length = 4000)
    var lastError: String? = null,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
