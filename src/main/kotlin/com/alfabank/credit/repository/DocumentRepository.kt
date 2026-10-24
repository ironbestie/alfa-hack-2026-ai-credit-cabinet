package com.alfabank.credit.repository

import com.alfabank.credit.domain.Document
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DocumentRepository : JpaRepository<Document, UUID> {
    fun findByApplication_IdOrderByUploadDateAsc(applicationId: UUID): List<Document>
    fun findByIdAndApplication_Id(id: UUID, applicationId: UUID): Document?
}