package com.alfabank.credit.integration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class AiAssistantClient(
    @Value("\${app.ollama.url:http://localhost:11434}") private val ollamaUrl: String
) {
    private val log = LoggerFactory.getLogger(AiAssistantClient::class.java)
    private val restClient = RestClient.builder()
        .baseUrl(ollamaUrl)
        .requestFactory(org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3000)
            setReadTimeout(5000)
        })
        .build()

    fun generateExplanation(prompt: String): String {
        return try {
            val request = mapOf(
                "model" to "qwen2.5:7b",
                "prompt" to prompt,
                "stream" to false
            )
            val response = restClient.post()
                .uri("/api/generate")
                .body(request)
                .retrieve()
                .body(Map::class.java)

            (response?.get("response") as? String) ?: getFallbackText()
        } catch (e: Exception) {
            log.warn("Ollama недоступна: ${e.message}. Используем шаблон.")
            getFallbackText()
        }
    }

    private fun getFallbackText(): String {
        return "Мы проанализировали вашу заявку на основе доступных данных. На решение повлияли факторы, указанные в карточке заявки. Рекомендуем ознакомиться с шагами для улучшения кредитного профиля."
    }
}