package com.alfabank.credit.config

import com.alfabank.credit.domain.Company
import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.repository.CompanyRepository
import com.alfabank.credit.repository.CreditProductRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

//==============TEMPORARY CLASS TO MOCK DATA!!!!!!!!================


@Component
class DataLoader(
    private val companyRepository: CompanyRepository,
    private val productRepository: CreditProductRepository
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(DataLoader::class.java)

    override fun run(vararg args: String?) {
        if (productRepository.count() > 0) return

        log.info("Loading synthetic data...")

        companyRepository.save(Company(
            inn = "7701234567",
            name = "ООО Ромашка",
            ogrn = "1234567890123",
            directorName = "Иванов Иван",
            registrationDate = LocalDate.of(2020, 1, 1),
            okved = "62.01",
            monthlyRevenue = BigDecimal("1500000")
        ))

        productRepository.saveAll(listOf(
            CreditProduct(name = "Оборотный кредит", minAmount = BigDecimal("100000"), maxAmount = BigDecimal("3000000"), minTermMonths = 3, maxTermMonths = 36, interestRate = 18.5),
            CreditProduct(name = "Кредит на развитие", minAmount = BigDecimal("500000"), maxAmount = BigDecimal("10000000"), minTermMonths = 6, maxTermMonths = 60, interestRate = 21.0, requiresCollateral = true)
        ))

        log.info("Synthetic data loaded successfully.")
    }
}