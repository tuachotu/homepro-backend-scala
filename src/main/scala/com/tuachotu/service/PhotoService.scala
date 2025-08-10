package com.tuachotu.service

import com.tuachotu.model.db.{Photo, PhotoWithDetails}
import com.tuachotu.model.response.{PhotoResponse, HomeItemInfo, UploadedByInfo}
import com.tuachotu.repository.PhotoRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PhotoService(
  photoRepository: PhotoRepository,
  s3Service: S3Service
)(implicit ec: ExecutionContext) {
  
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def getPhotosByHomeId(homeId: UUID): Future[List[PhotoResponse]] = {
    for {
      photosWithDetails <- photoRepository.findPhotosByHomeId(homeId)
      photoResponses <- convertToPhotoResponses(photosWithDetails)
    } yield {
      logger.info(s"Retrieved ${photoResponses.length} photos for home ID: $homeId")
      photoResponses
    }
  }

  def getPhotosByHomeItemId(homeItemId: UUID): Future[List[PhotoResponse]] = {
    for {
      photosWithDetails <- photoRepository.findPhotosByHomeItemId(homeItemId)
      photoResponses <- convertToPhotoResponses(photosWithDetails)
    } yield {
      logger.info(s"Retrieved ${photoResponses.length} photos for home item ID: $homeItemId")
      photoResponses
    }
  }

  private def convertToPhotoResponses(photosWithDetails: List[PhotoWithDetails]): Future[List[PhotoResponse]] = {
    Future.traverse(photosWithDetails) { photoWithDetails =>
      val photo = photoWithDetails.photo
      
      for {
        presignedUrl <- generateContextBasedPresignedUrl(photo)
      } yield {
        val homeItemInfo = photoWithDetails.homeItem.map { homeItem =>
          HomeItemInfo(
            id = homeItem.id.toString,
            name = homeItem.name,
            `type` = homeItem.itemType
          )
        }

        val uploadedByInfo = photoWithDetails.uploadedBy.map { user =>
          UploadedByInfo(
            id = user.id.toString,
            name = user.name.getOrElse("Unknown User"),
            email = user.email.getOrElse("No Email")
          )
        }

        PhotoResponse(
          id = photo.id.toString,
          file_name = photo.fileName.getOrElse("unknown"),
          caption = photo.caption,
          is_primary = photo.isPrimary,
          created_at = photo.createdAt.format(dateFormatter),
          url = presignedUrl,
          home_item = homeItemInfo,
          uploaded_by = uploadedByInfo
        )
      }
    }
  }
  
  /**
   * Generates a context-based presigned URL for a photo.
   * 
   * This method implements a priority-based context resolution:
   * 1. HomeItemId (highest priority) - for photos of specific home items
   * 2. HomeId (medium priority) - for general home photos
   * 3. UserId (lowest priority) - for user profile photos
   * 4. Fallback - uses s3Key directly for legacy photos
   * 
   * The resulting S3 path will be: {context_id}/{s3Key}
   * where s3Key is just the filename stored in the database.
   * 
   * @param photo The photo entity containing context information
   * @return Future containing the presigned URL
   */
  private def generateContextBasedPresignedUrl(photo: Photo): Future[String] = {
    // Determine the context and build the S3 path accordingly
    val contextId = if (photo.homeItemId.isDefined) {
      photo.homeItemId.get.toString
    } else if (photo.homeId.isDefined) {
      photo.homeId.get.toString
    } else if (photo.userId.isDefined) {
      photo.userId.get.toString
    } else {
      // Fallback to using s3Key as-is if no context is found
      return s3Service.generatePresignedUrl(photo.s3Key)
    }
    
    s3Service.generatePresignedUrlForContext(contextId, photo.s3Key)
  }
}