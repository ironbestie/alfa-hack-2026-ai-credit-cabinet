package com.alfabank.credit.repository
import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID
interface ApplicationRepository : JpaRepository<Application, UUID> {
    fun findByOwnerUsername(ownerUsername: String, pageable: Pageable): Page<Application>
    fun findByStatus(status: ApplicationStatus, pageable: Pageable): Page<Application>
    fun findByStatusInAndUpdatedAtBefore(
        statuses: Collection<ApplicationStatus>,
        updatedAt: Instant,
        pageable: Pageable
    ): Page<Application>

    @EntityGraph(attributePaths = ["company", "product"])
    @Query("select a from Application a where a.id = :id")
    fun findWithDetailsById(@Param("id") id: UUID): Application?

    @Modifying
    @Query(value = "UPDATE applications SET version = 0 WHERE version IS NULL", nativeQuery = true)
    fun backfillVersions(): Int
}
