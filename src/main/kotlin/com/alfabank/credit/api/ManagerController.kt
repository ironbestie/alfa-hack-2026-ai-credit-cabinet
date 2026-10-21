package com.alfabank.credit.api

import com.alfabank.credit.domain.ApplicationStatus
import com.alfabank.credit.repository.ApplicationRepository
import com.alfabank.credit.service.ApplicationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ManagerDecisionRequest(val isApproved: Boolean, val comment: String)

@RestController
@RequestMapping("/api/manager")
class ManagerController(
    private val applicationRepository: ApplicationRepository,
    private val applicationService: ApplicationService
) {
    @GetMapping("/applications/pending")
    fun getPendingApplications(): ResponseEntity<Any> {
        val apps = applicationRepository.findAll().filter { it.status == ApplicationStatus.MANUAL_REVIEW }
        return ResponseEntity.ok(apps.map { mapOf("id" to it.id, "amount" to it.requestedAmount, "score" to it.riskScore) })
    }

    @PostMapping("/applications/{id}/decision")
    fun makeDecision(@PathVariable id: UUID, @RequestBody request: ManagerDecisionRequest): ResponseEntity<Any> {
        return try {
            applicationService.processManagerDecision(id, request.isApproved, request.comment)
            ResponseEntity.ok(mapOf("message" to "Decision processed"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}