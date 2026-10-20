package com.alfabank.credit.repository

import com.alfabank.credit.domain.ApplicationEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ApplicationEventRepository : JpaRepository<ApplicationEvent, UUID> {
    fun findByApplicationIdOrderByTimestampAsc(applicationId: UUID): List<ApplicationEvent>
}