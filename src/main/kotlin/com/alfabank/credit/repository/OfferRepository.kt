package com.alfabank.credit.repository

import com.alfabank.credit.domain.Offer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OfferRepository : JpaRepository<Offer, UUID> {
    fun findByApplicationId(applicationId: UUID): List<Offer>
}