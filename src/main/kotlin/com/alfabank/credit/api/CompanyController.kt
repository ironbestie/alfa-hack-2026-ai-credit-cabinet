package com.alfabank.credit.api

import com.alfabank.credit.domain.Company
import com.alfabank.credit.integration.EgrulClient
import com.alfabank.credit.integration.EgrulData
import com.alfabank.credit.repository.CompanyRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Компании", description = "Справочник компаний и данные ЕГРЮЛ")
@RestController
@RequestMapping("/api/companies")
class CompanyController(
    private val egrulClient: EgrulClient,
    private val companyRepository: CompanyRepository
) {
    @Operation(summary = "Список компаний", description = "Возвращает компании, загруженные в систему (нужно для получения companyId).")
    @GetMapping
    fun listCompanies(): List<Company> {
        return companyRepository.findAll()
    }

    @Operation(summary = "Карточка компании по ИНН", description = "Сначала ищет компанию в БД, затем обращается в ЕГРЮЛ.")
    @GetMapping("/lookup")
    fun lookupCompany(@RequestParam inn: String): ResponseEntity<Any> {
        val company = companyRepository.findByInn(inn)
        if (company != null) {
            return ResponseEntity.ok(
                EgrulData(
                    inn = company.inn,
                    name = company.name,
                    ogrn = company.ogrn,
                    directorName = company.directorName,
                    registrationDate = company.registrationDate,
                    okved = "${company.okved} (${company.okvedDescription})"
                )
            )
        }

        val data = egrulClient.fetchCompanyByInn(inn)
        return if (data != null) ResponseEntity.ok(data) else ResponseEntity.notFound().build()
    }
}
