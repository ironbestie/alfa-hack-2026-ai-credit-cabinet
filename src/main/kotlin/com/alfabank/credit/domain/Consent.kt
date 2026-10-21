package com.alfabank.credit.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "consents")
class Consent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    val application: Application,

    @Column(nullable = false)
    val consentType: String,

    @Column(nullable = false)
    val signedAt: Instant = Instant.now()
)