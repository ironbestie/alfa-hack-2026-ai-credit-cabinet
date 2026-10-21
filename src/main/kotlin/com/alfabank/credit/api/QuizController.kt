package com.alfabank.credit.api

import com.alfabank.credit.service.QuizService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

data class QuizRequest(val purpose: String, val amount: BigDecimal, val termMonths: Int)

@RestController
@RequestMapping("/api/quiz")
class QuizController(
    private val quizService: QuizService
) {
    @PostMapping("/recommend")
    fun recommendProduct(@RequestBody request: QuizRequest): ResponseEntity<Any> {
        val recommended = quizService.recommendProducts(request.purpose, request.amount)
        return ResponseEntity.ok(recommended)
    }
}