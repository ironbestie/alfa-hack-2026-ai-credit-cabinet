package com.alfabank.credit.api

import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.dto.PageResponse
import com.alfabank.credit.repository.CreditProductRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Продукты", description = "Справочник кредитных продуктов")
@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productRepository: CreditProductRepository
) {
    @Operation(summary = "Список продуктов", description = "Возвращает доступные кредитные продукты (нужно для получения productId).")
    @GetMapping
    fun listProducts(
        @Parameter(description = "Номер страницы (с 0)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Размер страницы (1-100)") @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<CreditProduct> {
        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "name"))
        return PageResponse.from(productRepository.findAll(pageable))
    }
}
