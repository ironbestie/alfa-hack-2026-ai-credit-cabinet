package com.alfabank.credit.api

import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.repository.CreditProductRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Продукты", description = "Справочник кредитных продуктов")
@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productRepository: CreditProductRepository
) {
    @Operation(summary = "Список продуктов", description = "Возвращает доступные кредитные продукты (нужно для получения productId).")
    @GetMapping
    fun listProducts(): List<CreditProduct> {
        return productRepository.findAll()
    }
}
