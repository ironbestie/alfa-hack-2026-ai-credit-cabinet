package com.alfabank.credit.service

import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.repository.CreditProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class QuizService(
    private val productRepository: CreditProductRepository
) {
    @Transactional(readOnly = true)
    fun recommendProducts(purpose: String, amount: BigDecimal): List<CreditProduct> {
        return productRepository.findAll()
            .filter { amount >= it.minAmount && amount <= it.maxAmount }
            .sortedByDescending { product ->
                val hasCollateralTag = product.tags.contains("С залогом")
                if (purpose == "ОБОРУДОВАНИЕ" && hasCollateralTag) 100.0 else -product.baseRate
            }
            .take(2)
    }
}