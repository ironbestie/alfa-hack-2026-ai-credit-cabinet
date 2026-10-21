package com.alfabank.credit.api

import com.alfabank.credit.exception.InvalidStateTransitionException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidStateTransitionException::class)
    fun handleInvalidStateTransition(ex: InvalidStateTransitionException): ResponseEntity<Map<String, String>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(mapOf("error" to (ex.message ?: "Недопустимый переход статуса")))
    }
}