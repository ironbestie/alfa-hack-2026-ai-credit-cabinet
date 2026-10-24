package com.alfabank.credit.api

import com.alfabank.credit.dto.DeadLetterResponse
import com.alfabank.credit.dto.ManagerPendingApplicationResponse
import com.alfabank.credit.dto.PageResponse
import com.alfabank.credit.service.ApplicationDecisionService
import com.alfabank.credit.service.ApplicationService
import com.alfabank.credit.service.processing.ProcessingJobService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class ManagerDecisionRequest(
    val decisionType: String,
    val approvedAmount: BigDecimal? = null,
    val approvedTermMonths: Int? = null,
    val comment: String = "",
    val reasonCode: String? = null
)

@Tag(name = "Кабинет менеджера", description = "Ручное рассмотрение заявок (серые зоны)")
@RestController
@RequestMapping("/api/manager")
class ManagerController(
    private val applicationService: ApplicationService,
    private val decisionService: ApplicationDecisionService,
    private val processingJobService: ProcessingJobService
) {
    @Operation(summary = "Очередь заявок на ручное рассмотрение", description = "Возвращает заявки в статусе MANUAL_REVIEW с пагинацией.")
    @GetMapping("/applications/pending")
    fun getPendingApplications(
        @Parameter(description = "Номер страницы (с 0)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Размер страницы (1-100)") @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<ManagerPendingApplicationResponse> {
        return applicationService.getPendingApplicationsForManager(page, size)
    }

    @Operation(
        summary = "Вынести решение по заявке",
        description = "Менеджер может одобрить (APPROVE), сделать контрпредложение (COUNTER_OFFER), запросить документы (REQUEST_DOCS) или отказать (REJECT)."
    )
    @PostMapping("/applications/{id}/decision")
    fun makeDecision(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID,
        @RequestBody request: ManagerDecisionRequest
    ): Map<String, String> {
        decisionService.processManagerDecision(
            applicationId = id,
            decisionType = request.decisionType,
            approvedAmount = request.approvedAmount,
            approvedTermMonths = request.approvedTermMonths,
            comment = request.comment,
            reasonCode = request.reasonCode
        )
        return mapOf("message" to "Решение обработано")
    }

    @Operation(
        summary = "Очередь недоставленных событий (DLQ)",
        description = "Возвращает заявки, обработка которых завершилась ошибкой после всех повторных попыток."
    )
    @GetMapping("/dlq")
    fun getDeadLetters(
        @Parameter(description = "Номер страницы (с 0)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Размер страницы (1-100)") @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<DeadLetterResponse> {
        return processingJobService.getDeadLetters(page, size)
    }

    @Operation(
        summary = "Повторить обработку из DLQ",
        description = "Сбрасывает счётчик попыток и возвращает заявку в очередь периодической обработки."
    )
    @PostMapping("/dlq/{applicationId}/retry")
    fun retryDeadLetter(
        @Parameter(description = "UUID заявки") @PathVariable applicationId: UUID
    ): Map<String, String> {
        processingJobService.replay(applicationId)
        return mapOf("message" to "Задача возвращена в очередь повторной обработки")
    }
}
