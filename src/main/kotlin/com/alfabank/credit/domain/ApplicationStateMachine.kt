package com.alfabank.credit.domain

import com.alfabank.credit.exception.InvalidStateTransitionException

object ApplicationStateMachine {

    private val allowedTransitions = mapOf(
        ApplicationStatus.DRAFT to setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.CANCELLED),
        ApplicationStatus.SUBMITTED to setOf(ApplicationStatus.ENRICHMENT, ApplicationStatus.NEEDS_MORE_INFO),
        ApplicationStatus.ENRICHMENT to setOf(ApplicationStatus.SCORING, ApplicationStatus.MANUAL_REVIEW),
        ApplicationStatus.SCORING to setOf(
            ApplicationStatus.APPROVED,
            ApplicationStatus.COUNTER_OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.MANUAL_REVIEW
        ),
        ApplicationStatus.MANUAL_REVIEW to setOf(
            ApplicationStatus.APPROVED,
            ApplicationStatus.COUNTER_OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.NEEDS_MORE_INFO
        ),
        ApplicationStatus.NEEDS_MORE_INFO to setOf(ApplicationStatus.SUBMITTED, ApplicationStatus.EXPIRED),
        ApplicationStatus.APPROVED to setOf(ApplicationStatus.SIGNING, ApplicationStatus.CANCELLED),
        ApplicationStatus.COUNTER_OFFER to setOf(ApplicationStatus.SIGNING, ApplicationStatus.CANCELLED),
        ApplicationStatus.SIGNING to setOf(ApplicationStatus.FUNDED),
        ApplicationStatus.REJECTED to emptySet(),
        ApplicationStatus.FUNDED to emptySet(),
        ApplicationStatus.CANCELLED to emptySet(),
        ApplicationStatus.EXPIRED to emptySet()
    )

    fun validateTransition(currentStatus: ApplicationStatus, nextStatus: ApplicationStatus) {
        val allowed = allowedTransitions[currentStatus] ?: emptySet()
        if (!allowed.contains(nextStatus)) {
            throw InvalidStateTransitionException("Недопустимый переход статуса: $currentStatus -> $nextStatus")
        }
    }
}