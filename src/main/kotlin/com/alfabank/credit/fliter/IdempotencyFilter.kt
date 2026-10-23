package com.alfabank.credit.api.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit

@Component
class IdempotencyFilter(
    private val redisTemplate: StringRedisTemplate
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method != "POST" || !request.requestURI.contains("/api/applications")) {
            filterChain.doFilter(request, response)
            return
        }

        val idempotencyKey = request.getHeader("Idempotency-Key")

        if (idempotencyKey == null) {
            filterChain.doFilter(request, response)
            return
        }

        val redisKey = "idempotency:$idempotencyKey"

        val isNewRequest = redisTemplate.opsForValue().setIfAbsent(redisKey, "in_progress", 24, TimeUnit.HOURS)

        if (isNewRequest == false) {
            response.status = HttpServletResponse.SC_CONFLICT
            response.writer.write("Duplicate request detected. Please wait.")
            return
        }

        try {
            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            redisTemplate.delete(redisKey)
            throw e
        }

        // Ключ фиксируем только для успешных ответов, чтобы клиент мог повторить запрос после ошибки.
        if (response.status < 400) {
            redisTemplate.opsForValue().set(redisKey, "completed", 24, TimeUnit.HOURS)
        } else {
            redisTemplate.delete(redisKey)
        }
    }
}
