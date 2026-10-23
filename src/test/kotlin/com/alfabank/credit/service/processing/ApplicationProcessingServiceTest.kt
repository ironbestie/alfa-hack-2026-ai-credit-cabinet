package com.alfabank.credit.service.processing

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.domain.Company
import com.alfabank.credit.domain.CreditProduct
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.repository.OfferRepository
import com.alfabank.credit.scoring.DecisionOutcome
import com.alfabank.credit.scoring.GradeResolver
import com.alfabank.credit.scoring.OfferOption
import com.alfabank.credit.scoring.ScoringInput
import com.alfabank.credit.scoring.TeamScoringResult
import com.alfabank.credit.service.SseService
import com.alfabank.credit.service.scoring.DecisionService
import com.alfabank.credit.service.scoring.EnrichmentPayload
import com.alfabank.credit.service.scoring.ScoringDetailsCodec
import com.alfabank.credit.service.scoring.ScoringPayload
import com.alfabank.credit.service.scoring.ScoringService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class ApplicationProcessingServiceTest {

    private val applicationRepository = mock(ApplicationRepository::class.java)
    private val eventRepository = mock(ApplicationEventRepository::class.java)
    private val offerRepository = mock(OfferRepository::class.java)
    private val scoringService = mock(ScoringService::class.java)
    private val decisionService = mock(DecisionService::class.java)
    private val scoringDetailsCodec = mock(ScoringDetailsCodec::class.java)
    private val gradeResolver = mock(GradeResolver::class.java)
    private val sseService = mock(SseService::class.java)

    private val service = ApplicationProcessingService(
        applicationRepository = applicationRepository,
        eventRepository = eventRepository,
        offerRepository = offerRepository,
        scoringService = scoringService,
        decisionService = decisionService,
        scoringDetailsCodec = scoringDetailsCodec,
        gradeResolver = gradeResolver,
        sseService = sseService,
        transactionManager = TestTransactionManager()
    )

    @Test
    fun `продолжает обработку из SCORING с сохранённым баллом`() {
        val applicationId = UUID.randomUUID()
        val application = application(applicationId, ApplicationStatus.SCORING, riskScore = 800)
        val input = input()
        val payload = EnrichmentPayload(input, sourcesOk = true)
        val offer = OfferOption(
            kind = "REQUESTED",
            amount = BigDecimal("3000000"),
            termMonths = 24,
            ratePercent = BigDecimal("22.0")
        )
        val expectedPayload = ScoringPayload(input, TeamScoringResult(800, "A", emptyList()), true)

        `when`(applicationRepository.findWithDetailsById(applicationId)).thenReturn(application)
        `when`(scoringService.enrich(application)).thenReturn(payload)
        `when`(scoringService.stopFactor(input)).thenReturn(null)
        `when`(scoringDetailsCodec.decode("[]")).thenReturn(emptyList())
        `when`(gradeResolver.gradeFor(800)).thenReturn("A")
        `when`(decisionService.makeDecision(application, expectedPayload))
            .thenReturn(DecisionOutcome("APPROVED", "Грейд A", listOf(offer)))
        `when`(offerRepository.findByApplicationId(applicationId)).thenReturn(emptyList())

        service.process(applicationId)

        assertEquals(ApplicationStatus.APPROVED, application.status)
        assertEquals(BigDecimal("3000000"), application.approvedAmount)
        verify(scoringService, never()).score(input)
        verify(decisionService).makeDecision(application, expectedPayload)
        verify(offerRepository).save(argThat { it.type == "REQUESTED" && it.amount == BigDecimal("3000000") })
        verify(eventRepository).save(argThat { it.eventType == ApplicationEventType.DECISION_MADE })
    }

    private fun application(
        id: UUID,
        status: ApplicationStatus,
        riskScore: Int?
    ): Application {
        val company = Company(
            inn = "7704983159",
            ogrn = "1187746198342",
            name = "ООО «ТехноПрогресс»",
            okved = "62.01",
            okvedDescription = "Разработка ПО",
            directorName = "Иванов Иван Иванович",
            registrationDate = LocalDate.of(2018, 3, 15),
            category = "Малое",
            status = "active",
            annualRevenue = BigDecimal("50000000"),
            totalDebt = BigDecimal("5000000"),
            taxDebt = BigDecimal.ZERO,
            bankRelationshipMonths = 36
        )
        val product = CreditProduct(
            name = "Оборотный кредит",
            description = "Тест",
            minAmount = BigDecimal("100000"),
            maxAmount = BigDecimal("30000000"),
            minTerm = 3,
            maxTerm = 36,
            baseRate = 22.0,
            tags = "[]"
        )
        return Application(
            id = id,
            company = company,
            product = product,
            requestedAmount = BigDecimal("3000000"),
            requestedTermMonths = 24,
            status = status,
            riskScore = riskScore,
            scoringDetails = "[]"
        )
    }

    private fun input() = ScoringInput(
        businessAgeMonths = 100,
        annualRevenue = BigDecimal("50000000"),
        totalDebt = BigDecimal("5000000"),
        taxDebt = BigDecimal.ZERO,
        okved = "62.01",
        bankRelationshipMonths = 36,
        maxOverdueDays = 0,
        activeLoansCount = 1,
        requestedAmount = BigDecimal("3000000"),
        termMonths = 24,
        hasCollateral = false
    )

    private class TestTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }
}
