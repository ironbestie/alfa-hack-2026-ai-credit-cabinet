package com.alfabank.credit.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "policy")
class PolicyProperties {
    var grades: Map<String, GradePolicy> = emptyMap()

    /** Сумма выше этого порога всегда уходит на ручное рассмотрение. */
    var manualReviewOver: BigDecimal = BigDecimal("10000000")
}

class GradePolicy {
    var rateSurcharge: Double = 0.0
    var maxAmountPctRevenue: Int = 0
    var maxAmountPctRevenueGuarantee: Int = 0
}
