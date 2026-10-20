package com.alfabank.credit.repository
import com.alfabank.credit.domain.CreditProduct
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface CreditProductRepository : JpaRepository<CreditProduct, UUID>