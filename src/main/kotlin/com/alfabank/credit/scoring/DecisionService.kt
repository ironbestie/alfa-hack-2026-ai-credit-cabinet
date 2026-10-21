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
                DecisionResult(ApplicationStatus.DECIDED, application.requestedAmount, "Кредит одобрен")
            }
            scoring.score in 40..69 -> {
                DecisionResult(ApplicationStatus.MANUAL_REVIEW, null, "Требуется ручное рассмотрение менеджером")
            }
            else -> {
                DecisionResult(ApplicationStatus.DECIDED, null, "Отказ: низкий уровень кредитоспособности (балл: ${scoring.score})")
            }
        }
    }
}