package com.alfabank.credit.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ApplicationStatus {
    DRAFT,
    ENRICHMENT,
    SCORING,
    DECISION,
    MANUAL_REVIEW,
    NEEDS_MORE_INFO,
    APPROVED,
    COUNTER_OFFER,
    REJECTED,
    SIGNING,
    FUNDED,
    CLOSED,

    // Legacy-значения: оставлены, чтобы старые строки в БД читались без миграции.
    SUBMITTED,
    CANCELLED,
    EXPIRED
}

@Entity
@Table(name = "applications")
class Application(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    val company: Company,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    val product: CreditProduct,

    @Column(nullable = false)
    val requestedAmount: BigDecimal,

    @Column(nullable = false)
    val requestedTermMonths: Int,

    val purpose: String? = null,

    @Column(nullable = false, columnDefinition = "boolean default false")
    var hasCollateral: Boolean = false,

    @Column(name = "owner_username")
    var ownerUsername: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ApplicationStatus = ApplicationStatus.DRAFT,

    @Version
    var version: Long? = null,

    var riskScore: Int? = null,

    @Column(length = 4000)
    var scoringDetails: String? = null,
    var approvedAmount: BigDecimal? = null,
    var approvedTermMonths: Int? = null,
    var rejectionReason: String? = null,
    var assignedManagerId: String? = null,

    @Column(unique = true)
    val idempotencyKey: String? = null,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
)