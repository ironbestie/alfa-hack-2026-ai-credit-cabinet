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
    val name: String,

    @Column(nullable = false)
    val ogrn: String,

    @Column(nullable = false)
    val directorName: String,

    val registrationDate: LocalDate,
    val okved: String,
    val monthlyRevenue: BigDecimal?
)