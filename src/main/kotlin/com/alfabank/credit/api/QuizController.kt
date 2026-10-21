package com.alfabank.credit.api

import com.alfabank.credit.repository.CreditProductRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

data class QuizRequest(val purpose: String, val amount: BigDecimal, val termMonths: Int)

@RestController
@RequestMapping("/api/quiz")
class QuizController(private val productRepository: CreditProductRepository) {

    @PostMapping("/recommend")
    fun recommendProduct(@RequestBody request: QuizRequest): ResponseEntity<Any> {
        val allProducts = productRepository.findAll()
        val recommended = allProducts.filter {
            request.amount >= it.minAmount && request.amount <= it.maxAmount
        }.sortedByDescending { product ->
            if (request.purpose == "ОБОРУДОВАНИЕ" && product.requiresCollateral) 100.0 else -product.interestRate
        }.take(2)

        return ResponseEntity.ok(recommended)
    }
}