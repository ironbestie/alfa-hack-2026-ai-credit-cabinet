package com.alfabank.credit.api

import com.alfabank.credit.service.ApplicationDecisionService
import com.alfabank.credit.service.ApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class ManagerDecisionRequest(
    val decisionType: String,
    val approvedAmount: BigDecimal?,
    val approvedTermMonths: Int?,
    val comment: String
)

@Tag(name = "Кабинет менеджера", description = "Ручное рассмотрение заявок (серые зоны)")
@RestController
@RequestMapping("/api/manager")
class ManagerController(
    private val applicationService: ApplicationService,
    private val decisionService: ApplicationDecisionService
) {
    @Operation(summary = "Очередь заявок на ручное рассмотрение", description = "Возвращает заявки в статусе MANUAL_REVIEW.")
    @GetMapping("/applications/pending")
    fun getPendingApplications(): ResponseEntity<Any> {
        return ResponseEntity.ok(applicationService.getPendingApplicationsForManager())
    }

    @Operation(summary = "Вынести решение по заявке", description = "Менеджер может одобрить, сделать контрпредложение, запросить документы или отказать.")
    @PostMapping("/applications/{id}/decision")
    fun makeDecision(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID,
        @RequestBody request: ManagerDecisionRequest
    ): ResponseEntity<Any> {
        return try {
            decisionService.processManagerDecision(
                applicationId = id,
                decisionType = request.decisionType,
                approvedAmount = request.approvedAmount,
                approvedTermMonths = request.approvedTermMonths,
                comment = request.comment
            )
            ResponseEntity.ok(mapOf("message" to "Decision processed"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}