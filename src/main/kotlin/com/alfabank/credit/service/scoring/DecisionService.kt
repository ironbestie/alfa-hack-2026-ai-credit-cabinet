package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class DecisionResult(
    val status: ApplicationStatus,
    val approvedAmount: BigDecimal?,
    val approvedTermMonths: Int?,
    val rejectionReason: String?
)

@Service
class DecisionService {

    fun makeDecision(application: Application, scoring: ScoringResult): DecisionResult {
        return when {
            scoring.grade == "A" || scoring.grade == "B" -> {
                DecisionResult(
                    status = ApplicationStatus.APPROVED,
                    approvedAmount = application.requestedAmount,
                    approvedTermMonths = application.requestedTermMonths,
                    rejectionReason = null
                )
            }
            scoring.grade == "C" -> {
                DecisionResult(
                    status = ApplicationStatus.MANUAL_REVIEW,
                    approvedAmount = null,
                    approvedTermMonths = null,
                    rejectionReason = "Требуется ручное рассмотрение"
                )
            }
            scoring.grade == "D" -> {
                val alternativeAmount = application.requestedAmount.multiply(BigDecimal("0.7"))
                DecisionResult(
                    status = ApplicationStatus.COUNTER_OFFER,
                    approvedAmount = alternativeAmount,
                    approvedTermMonths = application.requestedTermMonths,
                    rejectionReason = "Сумма снижена из-за уровня риска"
                )
            }
            else -> {
                DecisionResult(
                    status = ApplicationStatus.REJECTED,
                    approvedAmount = null,
                    approvedTermMonths = null,
                    rejectionReason = "Отказ: низкий уровень кредитоспособности (балл: ${scoring.score})"
                )
            }
        }
    }
}