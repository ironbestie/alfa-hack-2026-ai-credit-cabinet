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
class MockEgrulClient(
    private val mockClient: MockIntegrationClient
) : EgrulClient {
    override fun fetchCompanyByInn(inn: String): EgrulData? {
        val data = mockClient.getEgrul(inn) ?: return null
        val registrationDate = data.registrationDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null

        val okved = listOfNotNull(
            data.okved?.code?.takeIf { it.isNotBlank() },
            data.okved?.name
        ).joinToString(" ")

        return EgrulData(
            inn = data.inn.ifBlank { inn },
            name = data.displayName,
            ogrn = data.ogrn.orEmpty(),
            directorName = data.director?.fullName.orEmpty(),
            registrationDate = registrationDate,
            okved = okved
        )
    }
}
