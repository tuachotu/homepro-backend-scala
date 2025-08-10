package com.tuachotu

import com.tuachotu.repository.{HomeRepository, PhotoRepository}
import com.tuachotu.service.{HomeService, PhotoService, S3Service}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.util.UUID
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}

/**
 * Integration test for S3 presigned URL generation with real database data.
 * 
 * This test verifies the end-to-end flow:
 * User → Home → Photos → Context-based S3 Presigned URLs
 * 
 * Usage:
 *   sbt "Test/runMain com.tuachotu.testS3Integration <user-id>"
 * 
 * Example:
 *   sbt "Test/runMain com.tuachotu.testS3Integration a8f65408-8bce-4662-8fb3-d072b1f6dd34"
 */
object S3IntegrationTest {
  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val logger: Logger = LoggerUtil.getLogger(getClass)
  
  @main def testS3Integration(userIdStr: String): Unit = {
    Try(UUID.fromString(userIdStr)) match {
      case Success(userId) =>
        val homeRepository = new HomeRepository()
        val photoRepository = new PhotoRepository()
        val s3Service = new S3Service()
        
        val homeService = new HomeService(homeRepository)
        val photoService = new PhotoService(photoRepository, s3Service)
        
        try {
          logger.info(s"🧪 S3 Integration Test - Testing user: $userId")
          
          // Step 1: Get user's homes
          val homesFuture = homeService.getHomesByUserId(userId)
          val homes = Await.result(homesFuture, Duration.Inf)
          
          logger.info(s"📍 Found ${homes.length} homes for user $userId")
          
          if (homes.nonEmpty) {
            val home = homes.head
            val homeId = UUID.fromString(home.id)
            logger.info(s"🏠 Home ID: ${home.id}")
            logger.info(s"🏠 Home Address: ${home.address}")
            
            // Step 2: Get photos for this home
            val photosFuture = photoService.getPhotosByHomeId(homeId)
            val photos = Await.result(photosFuture, Duration.Inf)
            
            logger.info(s"📸 Found ${photos.length} photos for home ${home.id}")
            
            if (photos.nonEmpty) {
              val photo = photos.head
              logger.info(s"📸 Photo ID: ${photo.id}")
              logger.info(s"📸 Photo Filename: ${photo.file_name}")
              logger.info(s"📸 Photo Caption: ${photo.caption.getOrElse("N/A")}")
              logger.info(s"📸 Is Primary: ${photo.is_primary}")
              
              // Step 3: Verify context-based presigned URL
              logger.info("🔗 Context-based Presigned URL:")
              logger.info(s"   Expected S3 Path: ${home.id}/{filename}")
              logger.info(s"   Generated URL: ${photo.url}")
              
              // Verify the URL contains the correct path structure
              if (photo.url.contains(s"${home.id}/")) {
                logger.info("✅ SUCCESS: URL contains correct context-based path structure!")
                logger.info(s"✅ Context ID (Home ID): ${home.id}")
                logger.info(s"✅ S3 Path Structure: {home_id}/{filename}")
                logger.info("✅ Integration test PASSED")
              } else {
                logger.error("❌ FAILED: URL does not contain expected path structure")
                logger.error("❌ Integration test FAILED")
              }
              
            } else {
              logger.info("⚠️  No photos found for this home - cannot test S3 URL generation")
            }
            
          } else {
            logger.info("⚠️  No homes found for this user - cannot test S3 URL generation")
          }
          
        } catch {
          case exception: Exception =>
            logger.error("❌ S3 Integration test failed", exception)
        } finally {
          s3Service.close()
        }
        
      case Failure(_) =>
        logger.error(s"❌ Invalid UUID format: $userIdStr")
    }
  }
}