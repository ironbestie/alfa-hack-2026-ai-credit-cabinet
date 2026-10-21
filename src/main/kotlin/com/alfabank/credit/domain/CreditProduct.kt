package com.alfabank.credit.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "products")
class CreditProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val description: String,

    @Column(nullable = false)
    val minAmount: BigDecimal,

    @Column(nullable = false)
    val maxAmount: BigDecimal,

    @Column(nullable = false)
    val minTerm: Int,

    @Column(nullable = false)
    val maxTerm: Int,

    @Column(nullable = false)
    val baseRate: Double,

    @Column(nullable = false, length = 1000)
    val tags: String
)