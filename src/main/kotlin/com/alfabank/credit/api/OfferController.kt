package com.alfabank.credit.api

import com.alfabank.credit.service.ApplicationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/offers")
class OfferController(
    private val applicationService: ApplicationService
) {
    @GetMapping("/preapproved")
    fun getPreapprovedOffer(@RequestParam inn: String): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(applicationService.getPreapprovedOffer(inn))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}