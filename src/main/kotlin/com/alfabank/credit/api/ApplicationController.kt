package com.alfabank.credit.api

import com.alfabank.credit.dto.ApplicationResponse
import com.alfabank.credit.dto.CreateApplicationRequest
import com.alfabank.credit.dto.EventResponse
import com.alfabank.credit.dto.ExplanationResponse
import com.alfabank.credit.dto.OfferResponse
import com.alfabank.credit.dto.PageResponse
import com.alfabank.credit.dto.ScoreResponse
import com.alfabank.credit.service.ApplicationDecisionService
import com.alfabank.credit.service.ApplicationDocumentService
import com.alfabank.credit.service.ApplicationEventService
import com.alfabank.credit.service.ApplicationService
import com.alfabank.credit.service.SseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

data class AcceptOfferRequest(val offerId: UUID? = null)

@Tag(name = "Заявки", description = "Создание, отправка и отслеживание статусов заявок")
@RestController
@RequestMapping("/api/applications")
class ApplicationController(
    private val applicationService: ApplicationService,
    private val decisionService: ApplicationDecisionService,
    private val documentService: ApplicationDocumentService,
    private val eventService: ApplicationEventService,
    private val sseService: SseService
) {

    @Operation(
        summary = "Создать черновик заявки",
        description = "Возвращает созданную заявку со статусом DRAFT. Опционально поддерживает заголовок Idempotency-Key."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Заявка создана"),
        ApiResponse(responseCode = "400", description = "Ошибка валидации суммы (вне лимитов продукта)", content = [Content(schema = Schema(implementation = Map::class))]),
        ApiResponse(responseCode = "404", description = "Компания или продукт не найдены", content = [Content(schema = Schema(implementation = Map::class))])
    ])
    @PostMapping
    fun createApplication(@RequestBody request: CreateApplicationRequest): ApplicationResponse {
        return applicationService.createApplication(request)
    }

    @Operation(summary = "Получить все заявки", description = "Возвращает заявки с пагинацией: клиент видит только свои, менеджер — все.")
    @GetMapping
    fun listApplications(
        @Parameter(description = "Номер страницы (с 0)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Размер страницы (1-100)") @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<ApplicationResponse> {
        return applicationService.getAllApplications(page, size)
    }

    @Operation(summary = "Получить карточку заявки", description = "Возвращает полную информацию о заявке, включая балл и причины.")
    @GetMapping("/{id}")
    fun getApplication(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): ApplicationResponse {
        return applicationService.getApplicationById(id)
    }

    @Operation(summary = "Подписать согласия (218-ФЗ и 152-ФЗ)", description = "Обязательный шаг перед отправкой. Без согласий submit не пройдет.")
    @PostMapping("/{id}/consents")
    fun signConsents(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): Map<String, String> {
        applicationService.signConsents(id)
        return mapOf("message" to "Consents signed")
    }

    @Operation(summary = "Отправить заявку", description = "Запускает асинхронный конвейер (ENRICHMENT -> SCORING -> DECISION).")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Заявка отправлена"),
        ApiResponse(responseCode = "409", description = "Не подписаны согласия или заявка не в статусе DRAFT")
    ])
    @PostMapping("/{id}/submit")
    fun submitApplication(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): Map<String, String> {
        applicationService.submitApplication(id)
        return mapOf("message" to "Application submitted successfully")
    }

    @Operation(summary = "Получить историю событий (Таймлайн)", description = "Возвращает ленту событий для трекера клиента с пагинацией.")
    @GetMapping("/{id}/timeline")
    fun getTimeline(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID,
        @Parameter(description = "Номер страницы (с 0)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Размер страницы (1-100)") @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<EventResponse> {
        return eventService.getApplicationTimeline(id, page, size)
    }

    @Operation(summary = "SSE-поток статусов", description = "Server-Sent Events для живого трекера без перезагрузки страницы.")
    @GetMapping("/{id}/stream")
    fun streamApplicationStatus(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): SseEmitter {
        applicationService.checkAccess(id)
        return sseService.subscribe(id.toString())
    }

    @Operation(
        summary = "Загрузить документ",
        description = "Используется, если заявка в статусе NEEDS_MORE_INFO (дозапрос документов). Возвращает заявку в работу."
    )
    @PostMapping("/{id}/documents")
    fun uploadDocument(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID,
        @Parameter(description = "Файл документа") @RequestParam("file") file: MultipartFile
    ): Map<String, String> {
        documentService.uploadDocument(id, file)
        return mapOf("message" to "Document uploaded")
    }

    @Operation(
        summary = "Принять контрпредложение",
        description = "Переводит заявку из APPROVED/COUNTER_OFFER в SIGNING. Если передан offerId — фиксируются условия выбранного оффера."
    )
    @PostMapping("/{id}/accept-offer")
    fun acceptOffer(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID,
        @RequestBody(required = false) request: AcceptOfferRequest?
    ): Map<String, String> {
        decisionService.acceptOffer(id, request?.offerId)
        return mapOf("message" to "Offer accepted")
    }

    @Operation(summary = "Подписать документы и получить деньги", description = "Переводит заявку в финальный статус FUNDED (имитация выдачи).")
    @PostMapping("/{id}/sign")
    fun signOffer(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): Map<String, String> {
        decisionService.signOffer(id)
        return mapOf("message" to "Documents signed, funds disbursed")
    }

    @Operation(summary = "Получить контрпредложения", description = "Возвращает список из 1-3 офферов, если заявка в статусе COUNTER_OFFER.")
    @GetMapping("/{id}/offers")
    fun getOffers(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): List<OfferResponse> {
        return decisionService.getOffers(id)
    }

    @Operation(summary = "Получить объяснение решения (H3)", description = "Возвращает резюме от LLM, причины (факторы) и следующие шаги.")
    @GetMapping("/{id}/explanation")
    fun getExplanation(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): ExplanationResponse {
        return applicationService.getExplanation(id)
    }

    @Operation(summary = "Получить балл и грейд (для экрана решения)", description = "Возвращает балл, грейд и вклад факторов.")
    @GetMapping("/{id}/score")
    fun getScore(
        @Parameter(description = "UUID заявки") @PathVariable id: UUID
    ): ScoreResponse {
        return applicationService.getScore(id)
    }
}
