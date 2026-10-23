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

    @Column(name = "original_file_name")
    val originalFileName: String? = null,

    @Column(name = "content_type")
    val contentType: String? = null,

    @Column(name = "size_bytes")
    val sizeBytes: Long? = null,

    @Column(name = "sha256", length = 64)
    val sha256: String? = null,

    @Column(nullable = false)
    val uploadDate: Instant = Instant.now()
)