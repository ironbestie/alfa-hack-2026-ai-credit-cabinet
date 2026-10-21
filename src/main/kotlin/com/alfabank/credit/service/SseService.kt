package com.alfabank.credit.service

import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

@Service
class SseService {
    private val emitters = ConcurrentHashMap<String, MutableList<SseEmitter>>()

    fun subscribe(applicationId: String): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        emitters.computeIfAbsent(applicationId) { mutableListOf() }.add(emitter)

        emitter.onCompletion { removeEmitter(applicationId, emitter) }
        emitter.onTimeout { removeEmitter(applicationId, emitter) }
        emitter.onError { removeEmitter(applicationId, emitter) }

        return emitter
    }

    fun sendUpdate(applicationId: String, status: String, message: String) {
        val appEmitters = emitters[applicationId] ?: return
        val deadEmitters = mutableListOf<SseEmitter>()

        for (emitter in appEmitters) {
            try {
                emitter.send(SseEmitter.event().name("status").data(mapOf("status" to status, "message" to message)))
            } catch (e: Exception) {
                deadEmitters.add(emitter)
            }
        }

        if (deadEmitters.isNotEmpty()) {
            appEmitters.removeAll(deadEmitters)
        }
    }

    private fun removeEmitter(applicationId: String, emitter: SseEmitter) {
        emitters[applicationId]?.remove(emitter)
    }
}