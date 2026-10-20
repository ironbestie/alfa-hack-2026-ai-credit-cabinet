package com.alfabank.credit.repository
import com.alfabank.credit.domain.Application
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface ApplicationRepository : JpaRepository<Application, UUID>