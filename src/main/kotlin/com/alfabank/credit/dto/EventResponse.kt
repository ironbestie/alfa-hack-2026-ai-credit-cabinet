package com.alfabank.credit.dto

import java.time.Instant

data class EventResponse(
    val timestamp: Instant,
    val eventType: String,
    val description: String,
    val actor: String
)