package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationStatus
import org.springframework.stereotype.Service
import java.math.BigDecimal

data class DecisionResult(
    val status: ApplicationStatus,
    val approvedAmount: BigDecimal?,
    val rejectionReason: String?
)

@Service
class DecisionService {

    fun makeDecision(application: Application, scoring: ScoringResult): DecisionResult {
        return when {
            scoring.score >= 70 -> {
                DecisionResult(
                    status = ApplicationStatus.DECIDED,
                    approvedAmount = application.requestedAmount,
                    rejectionReason = null
                )
            }
            scoring.score in 40..69 -> {
                val alternativeAmount = application.requestedAmount.multiply(0.7.toBigDecimal())
                DecisionResult(
                    status = ApplicationStatus.DECIDED,
                    approvedAmount = alternativeAmount,
                    rejectionReason = "Сумма снижена из-за среднего уровня риска"
                )
            }
            else -> {
                DecisionResult(
                    status = ApplicationStatus.DECIDED,
                    approvedAmount = null,
                    rejectionReason = "Отказ: низкий уровень кредитоспособности (балл: ${scoring.score})"
                )
            }
        }
    }
}