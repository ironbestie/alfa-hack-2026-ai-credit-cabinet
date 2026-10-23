package com.alfabank.credit.filter

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter(
    @Value("\${app.rate-limit.capacity:60}") private val capacity: Long,
    @Value("\${app.rate-limit.refill-per-minute:60}") private val refillPerMinute: Long
) : OncePerRequestFilter() {

    private val cache = ConcurrentHashMap<String, Bucket>()

    private fun resolveBucket(ip: String): Bucket {
        return cache.computeIfAbsent(ip) {
            val limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                .build()

            Bucket.builder().addLimit(limit).build()
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!request.requestURI.startsWith("/api/")) {
            filterChain.doFilter(request, response)
            return
        }

        val ip = request.remoteAddr
        val bucket = resolveBucket(ip)

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response)
        } else {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.writer.write("Too many requests. Please try again later.")
        }
    }
}
