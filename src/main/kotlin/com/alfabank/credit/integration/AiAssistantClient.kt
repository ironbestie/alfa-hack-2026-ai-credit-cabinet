package com.alfabank.credit.integration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * ИИ-шлюз с OpenAI-совместимым API.
 * Подходит и для Ollama (/v1/chat/completions), и для внешнего провайдера:
 * достаточно поменять base-url, api-key и model через переменные окружения.
 */
@Service
class AiAssistantClient(
    @Value("\${app.ai.base-url:http://localhost:11434}") baseUrl: String,
    @Value("\${app.ai.api-key:}") private val apiKey: String,
    @Value("\${app.ai.model:qwen2.5:7b}") private val model: String,
    @Value("\${app.ai.timeout-seconds:10}") timeoutSeconds: Long
) {
    private val log = LoggerFactory.getLogger(AiAssistantClient::class.java)

    private val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3000)
            setReadTimeout((timeoutSeconds * 1000).toInt())
        })
        .build()

    fun generateExplanation(prompt: String, fallback: String = DEFAULT_FALLBACK): String {
        return try {
            val request = mapOf(
                "model" to model,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                    mapOf("role" to "user", "content" to prompt)
                ),
                "temperature" to 0.2,
                "stream" to false
            )
            val response = restClient.post()
                .uri("/v1/chat/completions")
                .headers { headers ->
                    if (apiKey.isNotBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                    }
                }
                .body(request)
                .retrieve()
                .body(Map::class.java)

            val content = ((response?.get("choices") as? List<*>)
                ?.firstOrNull() as? Map<*, *>)
                ?.get("message")
                ?.let { it as? Map<*, *> }
                ?.get("content") as? String

            if (content.isNullOrBlank() || !numbersAreGrounded(content, prompt)) {
                log.warn("ИИ вернул пустой ответ или числа, которых нет во входных данных. Используем шаблон.")
                fallback
            } else {
                content.trim()
            }
        } catch (e: Exception) {
            log.warn("ИИ недоступен: ${e.message}. Используем шаблон.")
            fallback
        }
    }

    /**
     * Простая защита от галлюцинаций: все числа в ответе должны встречаться в фактах.
     */
    private fun numbersAreGrounded(answer: String, facts: String): Boolean {
        val factNumbers = NUMBER_REGEX.findAll(facts)
            .map { it.value.replace(',', '.') }
            .toSet()
        val answerNumbers = NUMBER_REGEX.findAll(answer)
            .map { it.value.replace(',', '.') }
            .toSet()
        return answerNumbers.all { it in factNumbers }
    }

    companion object {
        private val NUMBER_REGEX = Regex("\\d+(?:[.,]\\d+)?")
        private const val SYSTEM_PROMPT =
            "Ты — вежливый банковский ассистент. Пиши по-русски, кратко и понятно. " +
                    "Не придумывай суммы, сроки, ставки и другие числа."
        private const val DEFAULT_FALLBACK =
            "Мы проанализировали вашу заявку на основе доступных данных. " +
                    "На решение повлияли факторы, указанные в карточке заявки. " +
                    "Рекомендуем ознакомиться с шагами для улучшения кредитного профиля."
    }
}
