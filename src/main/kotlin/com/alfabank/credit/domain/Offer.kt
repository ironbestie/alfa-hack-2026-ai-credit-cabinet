package com.alfabank.credit.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "offers")
class Offer(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    val application: Application,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Column(nullable = false)
    val termMonths: Int,

    @Column(nullable = false)
    val interestRate: Double,

    @Column(nullable = false)
    val type: String
)