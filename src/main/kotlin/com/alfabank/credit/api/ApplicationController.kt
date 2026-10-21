package com.alfabank.credit.api

import com.alfabank.credit.dto.ApplicationResponse
import com.alfabank.credit.dto.CreateApplicationRequest
import com.alfabank.credit.dto.EventResponse
import com.alfabank.credit.service.ApplicationDecisionService
import com.alfabank.credit.service.ApplicationDocumentService
import com.alfabank.credit.service.ApplicationEventService
import com.alfabank.credit.service.ApplicationService
import com.alfabank.credit.service.SseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/applications")
class ApplicationController(
    private val applicationService: ApplicationService,
    private val decisionService: ApplicationDecisionService,
    private val documentService: ApplicationDocumentService,
    private val eventService: ApplicationEventService,
    private val sseService: SseService
) {

    @PostMapping
    fun createApplication(@RequestBody request: CreateApplicationRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(applicationService.createApplication(request))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping
    fun listApplications(): List<ApplicationResponse> {
        return applicationService.getAllApplications()
    }

    @GetMapping("/{id}")
    fun getApplication(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(applicationService.getApplicationById(id))
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/consents")
    fun signConsents(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            applicationService.signConsents(id)
            ResponseEntity.ok(mapOf("message" to "Consents signed"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/submit")
    fun submitApplication(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            applicationService.submitApplication(id)
            ResponseEntity.ok(mapOf("message" to "Application submitted successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/accept-offer")
    fun acceptOffer(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            decisionService.acceptOffer(id)
            ResponseEntity.ok(mapOf("message" to "Offer accepted"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/sign")
    fun signOffer(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            decisionService.signOffer(id)
            ResponseEntity.ok(mapOf("message" to "Documents signed, funds disbursed"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @PostMapping("/{id}/documents")
    fun uploadDocument(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<Any> {
        return try {
            documentService.uploadDocument(id, file)
            ResponseEntity.ok(mapOf("message" to "Document uploaded"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}/timeline")
    fun getTimeline(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(eventService.getApplicationTimeline(id))
        } catch (e: Exception) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/stream")
    fun streamApplicationStatus(@PathVariable id: UUID): org.springframework.web.servlet.mvc.method.annotation.SseEmitter {
        return sseService.subscribe(id.toString())
    }

    @GetMapping("/{id}/offers")
    fun getOffers(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(decisionService.getOffers(id))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }

    @GetMapping("/{id}/explanation")
    fun getExplanation(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(applicationService.getExplanation(id))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}