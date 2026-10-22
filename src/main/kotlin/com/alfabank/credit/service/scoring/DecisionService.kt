package com.alfabank.credit.service.scoring

import com.alfabank.credit.domain.Application
import com.alfabank.credit.scoring.DecisionOutcome
import com.alfabank.credit.scoring.TeamScoring
import org.springframework.stereotype.Service

@Service
class DecisionService {
    fun makeDecision(application: Application, payload: ScoringPayload): DecisionOutcome {
        return TeamScoring.decide(
            input = payload.input,
            productRatePercent = application.product.baseRate.toBigDecimal()
        )
    }
}