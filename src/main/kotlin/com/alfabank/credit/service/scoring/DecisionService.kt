package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.scoring.DecisionOutcome
import com.alfabank.credit.scoring.TeamScoringService
import org.springframework.stereotype.Service

@Service
class DecisionService(
    private val teamScoringService: TeamScoringService // <-- Внедрили
) {
    fun makeDecision(application: Application, payload: ScoringPayload): DecisionOutcome {
        return teamScoringService.decide( // <-- Вызов
            input = payload.input,
            productRatePercent = application.product.baseRate.toBigDecimal()
        )
    }
}