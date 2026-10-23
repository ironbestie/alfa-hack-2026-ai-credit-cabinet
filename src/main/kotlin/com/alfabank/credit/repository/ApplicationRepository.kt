package com.alfabank.credit.repository
import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID
interface ApplicationRepository : JpaRepository<Application, UUID> {
    fun findByOwnerUsername(ownerUsername: String, pageable: Pageable): Page<Application>
    fun findByStatus(status: ApplicationStatus, pageable: Pageable): Page<Application>

    /**
     * Старые строки появились до @Version и имеют NULL. Без этого Hibernate не сможет их обновить.
     */
    @Modifying
    @Query(value = "UPDATE applications SET version = 0 WHERE version IS NULL", nativeQuery = true)
    fun backfillVersions(): Int
}
