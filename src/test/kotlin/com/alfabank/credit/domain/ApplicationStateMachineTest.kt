package com.alfabank.credit.domain

import com.alfabank.credit.exception.InvalidStateTransitionException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ApplicationStateMachineTest {

    @Test
    fun `отправка заявки сразу переводит DRAFT в ENRICHMENT`() {
        assertDoesNotThrow {
            ApplicationStateMachine.validateTransition(ApplicationStatus.DRAFT, ApplicationStatus.ENRICHMENT)
        }
        assertThrows(InvalidStateTransitionException::class.java) {
            ApplicationStateMachine.validateTransition(ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED)
        }
    }

    @Test
    fun `конвейер проходит через DECISION`() {
        assertDoesNotThrow {
            ApplicationStateMachine.validateTransition(ApplicationStatus.ENRICHMENT, ApplicationStatus.SCORING)
            ApplicationStateMachine.validateTransition(ApplicationStatus.SCORING, ApplicationStatus.DECISION)
            ApplicationStateMachine.validateTransition(ApplicationStatus.DECISION, ApplicationStatus.APPROVED)
        }
    }

    @Test
    fun `дозапрос документов возвращает заявку менеджеру`() {
        assertDoesNotThrow {
            ApplicationStateMachine.validateTransition(
                ApplicationStatus.NEEDS_MORE_INFO,
                ApplicationStatus.MANUAL_REVIEW
            )
        }
    }

    @Test
    fun `запрещён переход из DRAFT сразу в APPROVED`() {
        assertThrows(InvalidStateTransitionException::class.java) {
            ApplicationStateMachine.validateTransition(ApplicationStatus.DRAFT, ApplicationStatus.APPROVED)
        }
    }

    @Test
    fun `сбой источника на этапе скоринга уводит заявку на ручную проверку`() {
        assertDoesNotThrow {
            ApplicationStateMachine.validateTransition(
                ApplicationStatus.SCORING,
                ApplicationStatus.MANUAL_REVIEW
            )
        }
    }
}
