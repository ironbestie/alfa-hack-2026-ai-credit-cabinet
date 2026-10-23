package com.alfabank.credit.api

import com.alfabank.credit.dto.PreapprovedOfferResponse
import com.alfabank.credit.service.ApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "Офферы", description = "Предодобренные предложения и контрпредложения")
@RestController
@RequestMapping("/api/offers")
class OfferController(
    private val applicationService: ApplicationService
) {
    @Operation(summary = "Получить предодобренное предложение по ИНН", description = "Возвращает предварительную сумму и условия для клиента (H4).")
    @GetMapping("/preapproved")
    fun getPreapprovedOffer(
        @Parameter(description = "ИНН компании", example = "7703168426") @RequestParam inn: String
    ): PreapprovedOfferResponse {
        return applicationService.getPreapprovedOffer(inn)
    }
}
