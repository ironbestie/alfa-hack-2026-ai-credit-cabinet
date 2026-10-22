package com.alfabank.credit.dto

data class ScoreFactorDto(
    val name: String,
    val contribution: Int,
    val description: String
)

data class ScoreResponse(
    val score: Int,
    val grade: String,
    val riskLevel: String,
    val factors: List<ScoreFactorDto>
)