package com.alfabank.credit.service.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class S3FileStorage(
    private val s3Client: S3Client,
    @Value("\${app.s3.bucket-name}") private val bucketName: String
) : DocumentStorage {

    private val log = LoggerFactory.getLogger(S3FileStorage::class.java)

    init {
        try {
            if (s3Client.listBuckets().buckets().none { it.name() == bucketName }) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build())
                log.info("Bucket '$bucketName' created.")
            }
        } catch (e: Exception) {
            log.warn("Could not create bucket on startup (MinIO might not be ready yet): ${e.message}")
        }
    }

    override fun store(file: MultipartFile, applicationId: String): String {
        if (file.isEmpty) throw IllegalArgumentException("File is empty")
        if (file.size > 10 * 1024 * 1024) throw IllegalArgumentException("File size exceeds 10MB")

        val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase() ?: "bin"
        val key = "applications/$applicationId/${UUID.randomUUID()}.$extension"

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.contentType)
                    .build(),
                RequestBody.fromInputStream(file.inputStream, file.size)
            )
            log.info("File uploaded to S3: $key")
            return key
        } catch (e: Exception) {
            log.error("Failed to upload file to S3", e)
            throw RuntimeException("Could not upload document", e)
        }
    }
}