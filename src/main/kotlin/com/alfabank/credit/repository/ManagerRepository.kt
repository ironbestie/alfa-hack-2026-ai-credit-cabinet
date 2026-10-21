package com.alfabank.credit.repository
import com.alfabank.credit.domain.Manager
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface ManagerRepository : JpaRepository<Manager, UUID>