package com.alfabank.credit.repository

import com.alfabank.credit.domain.Consent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ConsentRepository : JpaRepository<Consent, UUID> {
    fun findByApplicationId(applicationId: UUID): List<Consent>
}