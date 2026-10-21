package com.alfabank.credit.service

import com.alfabank.credit.domain.Application
import com.alfabank.credit.domain.ApplicationEvent
import com.alfabank.credit.domain.ApplicationEventType
import com.alfabank.credit.dto.EventResponse
import com.alfabank.credit.repository.ApplicationEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ApplicationEventService(
    private val eventRepository: ApplicationEventRepository
) {
    @Transactional
    fun saveEvent(application: Application, type: ApplicationEventType, description: String) {
        val event = ApplicationEvent(
            application = application,
            eventType = type,
            description = description
        )
        eventRepository.save(event)
    }

    @Transactional(readOnly = true)
    fun getApplicationTimeline(applicationId: UUID): List<EventResponse> {
        return eventRepository.findByApplicationIdOrderByTimestampAsc(applicationId).map {
            EventResponse(
                timestamp = it.timestamp,
                eventType = it.eventType.name,
                description = it.description,
                actor = it.actor
            )
        }
    }
}