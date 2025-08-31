package com.tuachotu.service

import com.tuachotu.util.{ConfigUtil, LoggerUtil}
import com.tuachotu.util.LoggerUtil.Logger
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, PutObjectRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.core.sync.RequestBody

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

/**
 * S3Service provides methods for generating presigned URLs for AWS S3 objects.
 * 
 * This service supports both legacy direct S3 keys and the new context-based
 * S3 path construction where photos are organized by their context 
 * (user ID, home ID, or home item ID).
 * 
 * @param ec ExecutionContext for async operations
 */
class S3Service()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val bucketName = ConfigUtil.getString("aws.s3.bucket", "homepro-photos")
  private val region = Region.of(ConfigUtil.getString("aws.region", "us-east-1"))
  private val presignedUrlExpirationHours = ConfigUtil.getInt("aws.s3.presigned-url-expiration-hours", 24)
  
  // Initialize S3 client and presigner with default credentials
  private val s3Client: S3Client = S3Client.builder()
    .region(region)
    .build()
    
  private val s3Presigner: S3Presigner = S3Presigner.builder()
    .region(region)
    .build()
    
  /**
   * Generates a presigned URL for a given S3 key with configurable expiration.
   * 
   * @param s3Key The full S3 key/path for the object
   * @return Future containing the presigned URL
   */
  def generatePresignedUrl(s3Key: String): Future[String] = {
    Future {
      Try {
        val getObjectRequest = GetObjectRequest.builder()
          .bucket(bucketName)
          .key(s3Key)
          .build()
          
        val presignRequest = GetObjectPresignRequest.builder()
          .signatureDuration(Duration.ofHours(presignedUrlExpirationHours))
          .getObjectRequest(getObjectRequest)
          .build()
          
        val presignedUrl = s3Presigner.presignGetObject(presignRequest)
        presignedUrl.url().toString
      } match {
        case Success(url) =>
          logger.info(s"Generated pre-signed URL for S3 key: $s3Key", 
            "expirationHours", presignedUrlExpirationHours, 
            "bucket", bucketName)
          url
        case Failure(exception) =>
          logger.error(s"Failed to generate pre-signed URL for S3 key: $s3Key", exception)
          throw exception
      }
    }
  }
  
  /**
   * Generates a presigned URL using context-based S3 path construction.
   * 
   * This method builds the full S3 path by combining the context ID with the filename,
   * creating an organized folder structure in S3 based on the photo's context:
   * - User photos: {user_id}/{filename}
   * - Home photos: {home_id}/{filename} 
   * - Home item photos: {home_item_id}/{filename}
   * 
   * @param contextId The context identifier (user ID, home ID, or home item ID)
   * @param fileName The filename to be appended to the context path
   * @return Future containing the presigned URL
   */
  def generatePresignedUrlForContext(contextId: String, fileName: String): Future[String] = {
    val fullS3Key = s"$contextId/$fileName"
    generatePresignedUrl(fullS3Key)
  }
  
  /**
   * Uploads a file to S3 with the given key and content type.
   * 
   * @param s3Key The S3 key/path where the file should be stored
   * @param data The file data as byte array
   * @param contentType Optional content type for the file
   * @return Future that completes when upload is successful
   */
  def uploadFile(s3Key: String, data: Array[Byte], contentType: Option[String] = None): Future[Unit] = {
    Future {
      Try {
        val requestBuilder = PutObjectRequest.builder()
          .bucket(bucketName)
          .key(s3Key)
          
        val putObjectRequest = contentType match {
          case Some(ct) => requestBuilder.contentType(ct).build()
          case None => requestBuilder.build()
        }
        
        val requestBody = RequestBody.fromBytes(data)
        s3Client.putObject(putObjectRequest, requestBody)
      } match {
        case Success(_) =>
          logger.info(s"Successfully uploaded file to S3", 
            "s3Key", s3Key,
            "bucket", bucketName,
            "sizeBytes", data.length,
            "contentType", contentType.getOrElse("unknown"))
        case Failure(exception) =>
          logger.error(s"Failed to upload file to S3", exception,
            "s3Key", s3Key,
            "bucket", bucketName,
            "sizeBytes", data.length)
          throw exception
      }
    }
  }
  
  def close(): Unit = {
    try {
      s3Client.close()
      s3Presigner.close()
      logger.info("S3 client and presigner closed successfully")
    } catch {
      case exception: Exception =>
        logger.error("Failed to close S3 client and presigner", exception)
    }
  }
}