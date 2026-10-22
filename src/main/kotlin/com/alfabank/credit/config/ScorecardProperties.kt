package com.alfabank.credit.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "scorecard")
class ScorecardProperties {
    var base: BaseScore = BaseScore()
    var grades: Map<String, Int> = emptyMap()
}

class BaseScore {
    var baseValue: Int = 600
    var multiplier: Int = 2
}