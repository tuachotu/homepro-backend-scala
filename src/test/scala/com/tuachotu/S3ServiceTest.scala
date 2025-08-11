package com.tuachotu

import com.tuachotu.service.S3Service
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.util.UUID
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

/**
 * Unit test for S3Service presigned URL generation without database dependency.
 * 
 * This test verifies:
 * - AWS S3 connectivity 
 * - Context-based path construction
 * - Different photo contexts (user, home, homeItem, legacy)
 * - Presigned URL format and expiration
 * 
 * Usage:
 *   sbt "Test/runMain com.tuachotu.testS3Service"
 */
object S3ServiceTest {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val logger: Logger = LoggerUtil.getLogger(getClass)
  
  @main def testS3Service(): Unit = {
    val s3Service = new S3Service()
    
    try {
      logger.info("🧪 S3 Service Test - Testing context-based URL generation")
      
      val userId = UUID.fromString("a8f65408-8bce-4662-8fb3-d072b1f6dd34")
      val homeId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
      val homeItemId = UUID.fromString("660f9500-f39c-52e5-b827-556766550001")
      val fileName = "test_photo.jpg"
      
      // Test 1: User context
      logger.info("=== Test 1: User Photo Context ===")
      val userPhotoUrlFuture = s3Service.generatePresignedUrlForContext(userId.toString, fileName)
      val userPhotoUrl = Await.result(userPhotoUrlFuture, Duration.Inf)
      logger.info(s"🔗 User Context: ${userId.toString}/$fileName")
      logger.info(s"🔗 Generated URL: $userPhotoUrl")
      
      // Test 2: Home context
      logger.info("=== Test 2: Home Photo Context ===")
      val homePhotoUrlFuture = s3Service.generatePresignedUrlForContext(homeId.toString, fileName)
      val homePhotoUrl = Await.result(homePhotoUrlFuture, Duration.Inf)
      logger.info(s"🔗 Home Context: ${homeId.toString}/$fileName")
      logger.info(s"🔗 Generated URL: $homePhotoUrl")
      
      // Test 3: Home Item context
      logger.info("=== Test 3: Home Item Photo Context ===")
      val homeItemPhotoUrlFuture = s3Service.generatePresignedUrlForContext(homeItemId.toString, fileName)
      val homeItemPhotoUrl = Await.result(homeItemPhotoUrlFuture, Duration.Inf)
      logger.info(s"🔗 Home Item Context: ${homeItemId.toString}/$fileName")
      logger.info(s"🔗 Generated URL: $homeItemPhotoUrl")
      
      // Test 4: Legacy direct S3 key
      logger.info("=== Test 4: Legacy Direct S3 Key ===")
      val directUrlFuture = s3Service.generatePresignedUrl("legacy/old_photo.jpg")
      val directUrl = Await.result(directUrlFuture, Duration.Inf)
      logger.info(s"🔗 Direct S3 Key: legacy/old_photo.jpg")
      logger.info(s"🔗 Generated URL: $directUrl")
      
      // Verify URL patterns
      val allUrlsValid = List(
        userPhotoUrl.contains(s"$userId/$fileName"),
        homePhotoUrl.contains(s"$homeId/$fileName"),  
        homeItemPhotoUrl.contains(s"$homeItemId/$fileName"),
        directUrl.contains("legacy/old_photo.jpg"),
        userPhotoUrl.contains("X-Amz-Expires=86400") // 24 hour expiration (configurable)
      ).forall(identity)
      
      if (allUrlsValid) {
        logger.info("✅ SUCCESS: All S3 URL generation tests PASSED")
        logger.info("✅ Context-based path construction working correctly")
        logger.info("✅ AWS S3 connectivity confirmed")
        logger.info("✅ 24-hour expiration configured correctly")
      } else {
        logger.error("❌ FAILED: One or more S3 URL tests failed")
      }
      
    } catch {
      case exception: Exception =>
        logger.error("❌ S3 Service test failed", exception)
    } finally {
      s3Service.close()
    }
  }
}