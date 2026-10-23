package com.alfabank.credit.service

import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.repository.CreditProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class QuizService(private val productRepository: CreditProductRepository) {

    @Transactional(readOnly = true)
    fun recommendProducts(purpose: String, amount: BigDecimal, termMonths: Int): List<CreditProduct> {
        val allProducts = productRepository.findAll()

        val suitable = allProducts.filter {
            amount >= it.minAmount && amount <= it.maxAmount &&
                    termMonths >= it.minTerm && termMonths <= it.maxTerm
        }

        return suitable.sortedByDescending { product ->
            when {
                (purpose == "ЗАКУПКА" || purpose == "ОБОРОТ") && product.name.contains("Оборотный") -> 100.0
                purpose == "ОБОРУДОВАНИЕ" && product.name.contains("Инвестиционный") -> 100.0
                purpose == "ГОСКОНТРАКТ" && product.name.contains("господдержкой") -> 100.0
                purpose == "РАЗРЫВ" && product.name.contains("Овердрафт") -> 100.0
                else -> -product.baseRate
            }
        }.take(2)
    }
}