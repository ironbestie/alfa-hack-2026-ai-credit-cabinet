package com.alfabank.credit.integration

import org.springframework.stereotype.Service
import java.time.LocalDate

data class EgrulData(
    val inn: String,
    val name: String,
    val ogrn: String,
    val directorName: String,
    val registrationDate: LocalDate,
    val okved: String
)

interface EgrulClient {
    fun fetchCompanyByInn(inn: String): EgrulData?
}

@Service
class MockEgrulClient : EgrulClient {
    override fun fetchCompanyByInn(inn: String): EgrulData? {
        Thread.sleep(800) // Имитация задержки сети
        if (inn.length != 10 && inn.length != 12) return null

        return EgrulData(
            inn = inn,
            name = "ООО 'Умный Бизнес' (ИНН $inn)",
            ogrn = "1234567890123",
            directorName = "Петров Петр Петрович",
            registrationDate = LocalDate.of(2019, 5, 20),
            okved = "62.01 (Разработка ПО)"
        )
    }
}