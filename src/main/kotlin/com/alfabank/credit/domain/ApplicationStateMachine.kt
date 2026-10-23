package com.alfabank.credit.domain

import com.alfabank.credit.exception.InvalidStateTransitionException

object ApplicationStateMachine {

    private val allowedTransitions = mapOf(
        ApplicationStatus.DRAFT to setOf(ApplicationStatus.ENRICHMENT, ApplicationStatus.CLOSED),
        ApplicationStatus.SUBMITTED to setOf(ApplicationStatus.ENRICHMENT, ApplicationStatus.CLOSED),
        ApplicationStatus.ENRICHMENT to setOf(
            ApplicationStatus.SCORING,
            ApplicationStatus.MANUAL_REVIEW,
            ApplicationStatus.REJECTED,
            ApplicationStatus.CLOSED
        ),
        ApplicationStatus.SCORING to setOf(
            ApplicationStatus.DECISION,
            ApplicationStatus.MANUAL_REVIEW,
            ApplicationStatus.REJECTED
        ),
        ApplicationStatus.DECISION to setOf(
            ApplicationStatus.APPROVED,
            ApplicationStatus.COUNTER_OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.MANUAL_REVIEW
        ),
        ApplicationStatus.MANUAL_REVIEW to setOf(
            ApplicationStatus.APPROVED,
            ApplicationStatus.COUNTER_OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.NEEDS_MORE_INFO,
            ApplicationStatus.CLOSED
        ),
        ApplicationStatus.NEEDS_MORE_INFO to setOf(ApplicationStatus.MANUAL_REVIEW, ApplicationStatus.CLOSED),
        ApplicationStatus.APPROVED to setOf(ApplicationStatus.SIGNING, ApplicationStatus.CLOSED),
        ApplicationStatus.COUNTER_OFFER to setOf(ApplicationStatus.SIGNING, ApplicationStatus.CLOSED),
        ApplicationStatus.SIGNING to setOf(ApplicationStatus.FUNDED),
        ApplicationStatus.REJECTED to emptySet(),
        ApplicationStatus.FUNDED to emptySet(),
        ApplicationStatus.CLOSED to emptySet()
    )

    fun validateTransition(currentStatus: ApplicationStatus, nextStatus: ApplicationStatus) {
        val allowed = allowedTransitions[currentStatus] ?: emptySet()
        if (!allowed.contains(nextStatus)) {
            throw InvalidStateTransitionException("Недопустимый переход статуса: $currentStatus -> $nextStatus")
        }
    }
}
