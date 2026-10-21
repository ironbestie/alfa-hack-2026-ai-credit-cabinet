package com.alfabank.credit.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "managers")
class Manager(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val role: String
)