package com.alfabank.credit.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "documents")
class Document(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    val application: Application,

    @Column(nullable = false)
    val fileName: String,

    @Column(nullable = false)
    val uploadDate: Instant = Instant.now()
)