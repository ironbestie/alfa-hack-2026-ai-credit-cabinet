package com.alfabank.credit.service.scoring

import com.alfabank.credit.scoring.ScoreFactor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

data class ScoringFactorRecord(
    val code: String? = null,
    val label: String,
    val points: Int,
    val description: String = ""
)

@Component
class ScoringDetailsCodec(
    private val objectMapper: ObjectMapper
) {
    fun encode(factors: List<ScoreFactor>): String =
        objectMapper.writeValueAsString(
            factors.map {
                ScoringFactorRecord(
                    code = it.reasonCode,
                    label = it.name,
                    points = it.contribution,
                    description = it.description
                )
            }
        )

    fun decode(raw: String?): List<ScoringFactorRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val value = raw.trim()

        if (value.startsWith("[")) {
            return runCatching {
                objectMapper.readValue(value, object : TypeReference<List<ScoringFactorRecord>>() {})
            }.getOrDefault(emptyList())
        }

        return value.split("; ").mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val match = Regex("^(.*) \\((-?\\d+)\\)$").find(part)
            if (match != null) {
                ScoringFactorRecord(
                    label = match.groupValues[1],
                    points = match.groupValues[2].toIntOrNull() ?: 0
                )
            } else {
                ScoringFactorRecord(label = part, points = 0)
            }
        }
    }
}
