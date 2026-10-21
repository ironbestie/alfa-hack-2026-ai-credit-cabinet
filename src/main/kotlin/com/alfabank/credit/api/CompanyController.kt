package com.alfabank.credit.api

import com.alfabank.credit.integration.EgrulClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/companies")
class CompanyController(private val egrulClient: EgrulClient) {

    @GetMapping("/lookup")
    fun lookupCompany(@RequestParam inn: String): ResponseEntity<Any> {
        val data = egrulClient.fetchCompanyByInn(inn)
        return if (data != null) ResponseEntity.ok(data) else ResponseEntity.notFound().build()
    }
}