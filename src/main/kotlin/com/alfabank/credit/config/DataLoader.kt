package com.alfabank.credit.config

import com.alfabank.credit.domain.Company
import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.domain.Manager
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.CreditProductRepository
import com.alfabank.credit.repository.ManagerRepository // Нужно создать репозиторий!
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
        if (productRepository.count() > 0) {
            log.info("Data already loaded. Skipping.")
            return
        }

        log.info("Loading synthetic data...")

        companyRepository.save(Company(
            inn = "7707083893", ogrn = "1027700132195", name = "ООО «ТехноПрогресс»",
            okved = "62.01", okvedDescription = "Разработка компьютерного ПО",
            directorName = "Иванов Иван Иванович", registrationDate = LocalDate.of(2018, 3, 15),
            category = "Малое", status = "active", annualRevenue = BigDecimal("50000000"),
            totalDebt = BigDecimal("5000000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 36
        ))

        companyRepository.save(Company(
            inn = "7712345678", ogrn = "1167746123456", name = "ООО «Хлебный двор»",
            okved = "10.71", okvedDescription = "Производство хлеба",
            directorName = "Ковалёва Мария Сергеевна", registrationDate = LocalDate.of(2022, 3, 12),
            category = "Микропредприятие", status = "active", annualRevenue = BigDecimal("15000000"),
            totalDebt = BigDecimal("9300000"), taxDebt = BigDecimal("0"), bankRelationshipMonths = 24
        ))

        companyRepository.save(Company(
            inn = "7710140679", ogrn = "1157746123456", name = "ООО «СтройМаркет»",
            okved = "41.20", okvedDescription = "Строительство жилых зданий",
            directorName = "Петров Пётр Петрович", registrationDate = LocalDate.of(2025, 11, 1),
            category = "Микропредприятие", status = "active", annualRevenue = BigDecimal("5000000"),
            totalDebt = BigDecimal("4000000"), taxDebt = BigDecimal("150000"), bankRelationshipMonths = 8
        ))

        productRepository.saveAll(listOf(
            CreditProduct(name = "Оборотный кредит", description = "Лучше всего под закупку товара и пополнение оборотных средств", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("30000000"), minTerm = 3, maxTerm = 36, baseRate = 22.0, tags = "[\"Без залога\", \"Онлайн\"]"),
            CreditProduct(name = "Овердрафт", description = "Для краткосрочных кассовых разрывов", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("5000000"), minTerm = 1, maxTerm = 12, baseRate = 24.0, tags = "[\"Без залога\", \"Возобновляемый\"]"),
            CreditProduct(name = "Инвестиционный кредит", description = "На покупку оборудования и транспорта", minAmount = BigDecimal("500000"), maxAmount = BigDecimal("100000000"), minTerm = 12, maxTerm = 84, baseRate = 20.0, tags = "[\"С залогом\"]"),
            CreditProduct(name = "Кредит с господдержкой", description = "Поручительство Корпорации МСП", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("50000000"), minTerm = 3, maxTerm = 36, baseRate = 19.5, tags = "[\"Без залога\", \"Госпрограмма\"]")
        ))

        managerRepository.saveAll(listOf(
            Manager(name = "Анна К.", role = "credit_analyst"),
            Manager(name = "Сергей М.", role = "senior_analyst")
        ))

        log.info("Synthetic data loaded successfully.")
    }
}