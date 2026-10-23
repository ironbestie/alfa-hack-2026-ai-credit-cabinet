package com.alfabank.credit.api

import com.alfabank.credit.exception.InvalidStateTransitionException
import com.alfabank.credit.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.resource.NoResourceFoundException

data class ApiError(
    val error: String,
    val code: String
)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ApiError> =
        response(HttpStatus.NOT_FOUND, ex.message ?: "Не найдено", "NOT_FOUND")

    @ExceptionHandler(InvalidStateTransitionException::class)
    fun handleInvalidStateTransition(ex: InvalidStateTransitionException): ResponseEntity<ApiError> =
        response(
            HttpStatus.CONFLICT,
            ex.message ?: "Недопустимый переход статуса",
            "INVALID_STATE_TRANSITION"
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiError> =
        response(
            HttpStatus.CONFLICT,
            ex.message ?: "Операция невозможна в текущем состоянии",
            "ILLEGAL_STATE"
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, ex.message ?: "Некорректный запрос", "BAD_REQUEST")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "Некорректный JSON в запросе", "BAD_REQUEST")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ApiError> =
        response(
            HttpStatus.BAD_REQUEST,
            "Не передан обязательный параметр: ${ex.parameterName}",
            "BAD_REQUEST"
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(ex: MaxUploadSizeExceededException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "Размер файла превышает допустимый лимит", "FILE_TOO_LARGE")

    @ExceptionHandler(MultipartException::class)
    fun handleMultipart(ex: MultipartException): ResponseEntity<ApiError> =
        response(HttpStatus.BAD_REQUEST, "Запрос должен быть multipart/form-data", "INVALID_MULTIPART")

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> =
        response(
            HttpStatus.BAD_REQUEST,
            "Некорректное значение параметра: ${ex.name}",
            "BAD_REQUEST"
        )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ApiError> =
        response(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Неподдерживаемый Content-Type. Используйте application/json.",
            "UNSUPPORTED_MEDIA_TYPE"
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ApiError> =
        response(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Метод ${ex.method} не поддерживается для этого эндпоинта",
            "METHOD_NOT_ALLOWED"
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ApiError> =
        response(HttpStatus.NOT_FOUND, "Ресурс не найден", "NOT_FOUND")

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: OptimisticLockingFailureException): ResponseEntity<ApiError> =
        response(
            HttpStatus.CONFLICT,
            "Заявка уже изменена другим пользователем. Обновите данные и повторите.",
            "CONFLICT"
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiError> =
        response(HttpStatus.FORBIDDEN, "Недостаточно прав для выполнения операции", "FORBIDDEN")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        log.error("Необработанная ошибка", ex)
        return response(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Внутренняя ошибка сервера",
            "INTERNAL_ERROR"
        )
    }

    private fun response(status: HttpStatus, message: String, code: String): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(message, code))
}
