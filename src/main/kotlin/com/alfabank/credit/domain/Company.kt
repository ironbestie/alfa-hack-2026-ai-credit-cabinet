package com.alfabank.credit.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "companies")
class Company(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val inn: String,

    @Column(nullable = false)
    val ogrn: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val okved: String,

    @Column(nullable = false)
    val okvedDescription: String,

    @Column(nullable = false)
    val directorName: String,

    @Column(nullable = false)
    val registrationDate: LocalDate,

    @Column(nullable = false)
    val category: String,

    @Column(nullable = false)
    val status: String,

    @Column(nullable = false)
    val annualRevenue: BigDecimal,

    @Column(nullable = false)
    val totalDebt: BigDecimal,

    @Column(nullable = false)
    val taxDebt: BigDecimal,

    @Column(nullable = false)
    val bankRelationshipMonths: Int
)