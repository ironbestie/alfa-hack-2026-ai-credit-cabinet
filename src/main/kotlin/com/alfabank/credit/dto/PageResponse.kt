package com.alfabank.credit.dto

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
) {
    companion object {
        fun <T> from(page: Page<T>): PageResponse<T> = PageResponse(
            items = page.content,
            page = page.number,
            size = page.size,
            totalItems = page.totalElements,
            totalPages = page.totalPages
        )

        fun pageable(page: Int, size: Int, sort: Sort = Sort.unsorted()): Pageable =
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100), sort)
    }
}
