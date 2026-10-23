package com.alfabank.credit.config

import com.alfabank.credit.domain.Company
import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.domain.Manager
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.CreditProductRepository
import com.alfabank.credit.repository.ManagerRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class DataLoader(
    private val companyRepository: CompanyRepository,
    private val productRepository: CreditProductRepository,
    private val managerRepository: ManagerRepository
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataLoader::class.java)

    override fun run(vararg args: String?) {
        seedCompanies()
        seedProducts()
        seedManagers()
    }

    /**
     * ИНН должны совпадать с данными в mocks/__files, иначе ЕГРЮЛ/ФНС/БКИ вернут 404
     * и заявка уйдёт в MANUAL_REVIEW из-за недоступности источников.
     */
    private fun seedCompanies() {
        val companies = listOf(
            Company(
                inn = "7704983159", ogrn = "1187746198342", name = "ООО «ТехноПрогресс»",
                okved = "62.01", okvedDescription = "Разработка компьютерного ПО",
                directorName = "Иванов Иван Иванович", registrationDate = LocalDate.of(2018, 3, 15),
                category = "Малое", status = "active", annualRevenue = BigDecimal("50000000"),
                totalDebt = BigDecimal("5000000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 36
            ),
            Company(
                inn = "7703168426", ogrn = "1227700512764", name = "ООО «Хлебный двор»",
                okved = "10.71", okvedDescription = "Производство хлеба",
                directorName = "Ковалёва Мария Сергеевна", registrationDate = LocalDate.of(2022, 3, 12),
                category = "Микропредприятие", status = "active", annualRevenue = BigDecimal("15000000"),
                totalDebt = BigDecimal("9300000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 24
            ),
            Company(
                inn = "7729501760", ogrn = "1257700384193", name = "ООО «СтройМаркет»",
                okved = "41.20", okvedDescription = "Строительство жилых зданий",
                directorName = "Петров Пётр Петрович", registrationDate = LocalDate.of(2025, 11, 1),
                category = "Микропредприятие", status = "active", annualRevenue = BigDecimal("5000000"),
                totalDebt = BigDecimal("4000000"), taxDebt = BigDecimal("150000"), bankRelationshipMonths = 8
            ),
            Company(
                inn = "7722085341", ogrn = "1217700446930", name = "ООО «Северный ветер»",
                okved = "46.39", okvedDescription = "Неспециализированная оптовая торговля пищевыми продуктами",
                directorName = "Соколов Андрей Викторович", registrationDate = LocalDate.of(2021, 4, 20),
                category = "Малое", status = "active", annualRevenue = BigDecimal("30000000"),
                totalDebt = BigDecimal("6000000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 18
            ),
            Company(
                inn = "7731497609", ogrn = "1197746022814", name = "ООО «Лесная поляна»",
                okved = "55.10", okvedDescription = "Деятельность гостиниц и прочих мест для временного проживания",
                directorName = "Белова Ольга Николаевна", registrationDate = LocalDate.of(2019, 6, 3),
                category = "Микропредприятие", status = "active", annualRevenue = BigDecimal("12000000"),
                totalDebt = BigDecimal("3000000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 0
            ),
            Company(
                inn = "7736150978", ogrn = "1177746281052", name = "ООО «Старый склад»",
                okved = "52.10", okvedDescription = "Деятельность по складированию и хранению",
                directorName = "Сидоров Олег Павлович", registrationDate = LocalDate.of(2017, 2, 10),
                category = "Микропредприятие", status = "liquidated", annualRevenue = BigDecimal("8000000"),
                totalDebt = BigDecimal("2000000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 0
            )
        )

        var added = 0
        companies.forEach { company ->
            if (companyRepository.findByInn(company.inn) == null) {
                companyRepository.save(company)
                added++
            }
        }
        log.info("Companies seeded: {} new, {} total", added, companyRepository.count())
    }

    private fun seedProducts() {
        if (productRepository.count() > 0) {
            log.info("Products already loaded. Skipping.")
            return
        }

        productRepository.saveAll(listOf(
            CreditProduct(name = "Оборотный кредит", description = "Лучше всего под закупку товара и пополнение оборотных средств", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("30000000"), minTerm = 3, maxTerm = 36, baseRate = 22.0, tags = "[\"Без залога\", \"Онлайн\"]"),
            CreditProduct(name = "Овердрафт", description = "Для краткосрочных кассовых разрывов", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("5000000"), minTerm = 1, maxTerm = 12, baseRate = 24.0, tags = "[\"Без залога\", \"Возобновляемый\"]"),
            CreditProduct(name = "Инвестиционный кредит", description = "На покупку оборудования и транспорта", minAmount = BigDecimal("500000"), maxAmount = BigDecimal("100000000"), minTerm = 12, maxTerm = 84, baseRate = 20.0, tags = "[\"С залогом\"]"),
            CreditProduct(name = "Кредит с господдержкой", description = "Поручительство Корпорации МСП", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("50000000"), minTerm = 3, maxTerm = 36, baseRate = 19.5, tags = "[\"Без залога\", \"Госпрограмма\"]")
        ))
        log.info("Synthetic products loaded successfully.")
    }

    private fun seedManagers() {
        if (managerRepository.count() > 0) return
        managerRepository.saveAll(listOf(
            Manager(name = "Анна К.", role = "credit_analyst"),
            Manager(name = "Сергей М.", role = "senior_analyst")
        ))
    }
}
