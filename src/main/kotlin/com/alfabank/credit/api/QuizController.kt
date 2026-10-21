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

        // Простая rule-based логика
        val recommended = allProducts.filter {
            request.amount >= it.minAmount && request.amount <= it.maxAmount
        }.sortedByDescending { product ->
            // Чем ниже ставка, тем лучше. Или если цель "оборудование", то отдаем приоритет продуктам с залогом
            if (request.purpose == "ОБОРУДОВАНИЕ" && product.requiresCollateral) 100.0 else -product.interestRate
        }.take(2)

        return ResponseEntity.ok(recommended)
    }
}