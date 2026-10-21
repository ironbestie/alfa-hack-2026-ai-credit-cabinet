package com.alfabank.credit.api

import com.alfabank.credit.dto.ApplicationResponse
import com.alfabank.credit.dto.CreateApplicationRequest
import com.alfabank.credit.service.ApplicationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/applications")
class ApplicationController(
    private val applicationService: ApplicationService
) {

    @PostMapping
    fun createApplication(@RequestBody request: CreateApplicationRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(applicationService.createApplication(request))
        } catch (e: IllegalArgumentException) {
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

    @GetMapping
    fun listApplications(): List<ApplicationResponse> {
        return applicationService.getAllApplications()
    }

    @GetMapping("/{id}")
    fun getApplication(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            val application = applicationService.getApplicationById(id)
            ResponseEntity.ok(application)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/{id}/timeline")
    fun getTimeline(@PathVariable id: UUID): ResponseEntity<Any> {
        return try {
            val timeline = applicationService.getApplicationTimeline(id)
            ResponseEntity.ok(timeline)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/{id}/documents")
    fun uploadDocument(
        @PathVariable id: UUID,
        @RequestParam("file") file: org.springframework.web.multipart.MultipartFile
    ): ResponseEntity<Any> {
        return try {
            applicationService.uploadDocument(id, file)
            ResponseEntity.ok(mapOf("message" to "Document uploaded"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to e.message))
        }
    }
}