package com.alfabank.credit.api

import com.alfabank.credit.service.ApplicationDecisionService
import com.alfabank.credit.service.ApplicationService
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

@RestController
@RequestMapping("/api/manager")
class ManagerController(
    private val applicationService: ApplicationService,
    private val decisionService: ApplicationDecisionService
) {
    @GetMapping("/applications/pending")
    fun getPendingApplications(): ResponseEntity<Any> {
        return ResponseEntity.ok(applicationService.getPendingApplicationsForManager())
    }

    @PostMapping("/applications/{id}/decision")
    fun makeDecision(@PathVariable id: UUID, @RequestBody request: ManagerDecisionRequest): ResponseEntity<Any> {
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