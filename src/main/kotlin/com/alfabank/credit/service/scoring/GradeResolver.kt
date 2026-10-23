package com.alfabank.credit.scoring

import com.alfabank.credit.config.ScorecardProperties
import org.springframework.stereotype.Component

/**
 * Единственный источник правды для грейдов: и скоринг, и экран решения используют YAML-конфиг.
 */
@Component
class GradeResolver(
    private val scorecard: ScorecardProperties
) {
    fun gradeFor(score: Int): String {
        return scorecard.grades.entries
            .sortedByDescending { it.value }
            .firstOrNull { score >= it.value }
            ?.key ?: "D"
    }

    fun riskLevel(grade: String): String = when (grade) {
        "A" -> "low"
        "D" -> "high"
        else -> "medium"
    }
}
