package com.alfabank.credit.domain

import java.util.UUID

data class ApplicationSubmittedEvent(
    val applicationId: UUID
)