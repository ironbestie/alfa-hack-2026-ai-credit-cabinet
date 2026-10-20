package com.alfabank.credit.repository
import com.alfabank.credit.domain.Company
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface CompanyRepository : JpaRepository<Company, UUID>