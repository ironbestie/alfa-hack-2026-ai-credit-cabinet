package com.alfabank.credit.service

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEvent
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.dto.EventResponse
import com.alfabank.credit.dto.PageResponse
import com.alfabank.credit.exception.NotFoundException
import com.alfabank.credit.repository.ApplicationEventRepository
import com.alfabank.credit.repository.ApplicationRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ApplicationEventService(
    private val eventRepository: ApplicationEventRepository,
    private val applicationRepository: ApplicationRepository,
    private val accessService: ApplicationAccessService
) {
    @Transactional
    fun saveEvent(
        application: Application,
        type: ApplicationEventType,
        description: String,
        actor: String = "SYSTEM"
    ) {
        val event = ApplicationEvent(
            application = application,
            eventType = type,
            description = description,
            actor = actor
        )
        eventRepository.save(event)
    }

    @Transactional(readOnly = true)
    fun getApplicationTimeline(applicationId: UUID, page: Int, size: Int): PageResponse<EventResponse> {
        val application = applicationRepository.findById(applicationId)
            .orElseThrow { NotFoundException("Application not found") }
        accessService.checkAccess(application)

        val pageable = PageResponse.pageable(page, size, Sort.by(Sort.Direction.ASC, "timestamp"))
        val events = eventRepository.findByApplicationIdOrderByTimestampAsc(applicationId, pageable)

        return PageResponse.from(events.map {
            EventResponse(
                timestamp = it.timestamp,
                eventType = it.eventType.name,
                description = it.description,
                actor = it.actor
            )
        })
    }
}
