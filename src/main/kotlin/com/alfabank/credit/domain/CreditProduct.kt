package com.alfabank.credit.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "credit_products")
class CreditProduct(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val minAmount: BigDecimal,

    @Column(nullable = false)
    val maxAmount: BigDecimal,

    @Column(nullable = false)
    val minTermMonths: Int,

    @Column(nullable = false)
    val maxTermMonths: Int,

    @Column(nullable = false)
    val interestRate: Double,

    val requiresCollateral: Boolean = false
)