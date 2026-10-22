package com.alfabank.credit.api

import com.alfabank.credit.service.QuizService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

data class QuizRequest(
    val purpose: String,
    val amount: BigDecimal,
    val termMonths: Int
)

@Tag(name = "Квиз", description = "Подбор кредитного продукта по цели, сумме и сроку")
@RestController
@RequestMapping("/api/quiz")
class QuizController(
    private val quizService: QuizService
) {
    @Operation(summary = "Рекомендовать продукт", description = "Возвращает 1-2 подходящих продукта на основе ответов квиза.")
    @PostMapping("/recommend")
    fun recommendProduct(@RequestBody request: QuizRequest): ResponseEntity<Any> {
        return try {
            val recommended = quizService.recommendProducts(request.purpose, request.amount, request.termMonths)
            ResponseEntity.ok(recommended)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}