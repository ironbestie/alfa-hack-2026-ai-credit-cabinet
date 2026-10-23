package com.alfabank.credit.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ApplicationEventType {
    APPLICATION_CREATED, STATUS_CHANGED, DATA_ENRICHED, SCORING_COMPLETED, DECISION_MADE, DOCUMENT_REQUESTED, DOCUMENT_UPLOADED, MANAGER_ASSIGNED, CLOSED
}

@Entity
@Table(name = "application_events")
class ApplicationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    val application: Application,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: ApplicationEventType,

    @Column(nullable = false, length = 1000)
    val description: String,

    val actor: String = "SYSTEM",

    @Column(nullable = false)
    val timestamp: Instant = Instant.now()
)