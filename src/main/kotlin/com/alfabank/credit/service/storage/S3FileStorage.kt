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
    private val documentValidator: DocumentValidator,
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

    override fun store(file: MultipartFile, applicationId: String): StoredDocument {
        val validated = documentValidator.validate(file)
        val key = "applications/$applicationId/${UUID.randomUUID()}.${validated.extension}"

        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(validated.contentType)
                    .build(),
                RequestBody.fromInputStream(file.inputStream, file.size)
            )
            log.info("File uploaded to S3: $key")
            return StoredDocument(
                key = key,
                originalFileName = validated.originalFileName,
                contentType = validated.contentType,
                sizeBytes = file.size,
                sha256 = validated.sha256
            )
        } catch (e: Exception) {
            log.error("Failed to upload file to S3", e)
            throw RuntimeException("Could not upload document", e)
        }
    }
}