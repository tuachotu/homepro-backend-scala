package com.tuachotu.service

import com.tuachotu.model.db.{Photo, PhotoWithDetails, HomeItemType}
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

  def deletePhoto(photoId: UUID, userId: UUID): Future[Unit] = {
    for {
      // Get the photo to verify it exists and check authorization
      photoOpt <- photoRepository.findPhotoById(photoId)
      photo <- photoOpt match {
        case Some(p) => Future.successful(p)
        case None => Future.failed(new RuntimeException("Photo not found"))
      }

      // Check authorization: user must own the home where this photo belongs
      _ <- checkPhotoDeleteAuthorization(photo, userId)

      // Delete from S3 first (non-blocking if it fails)
      _ <- deleteFromS3(photo).recover {
        case ex =>
          logger.warn(s"Failed to delete photo from S3: ${photo.s3Key}", ex)
          // Continue with database deletion even if S3 deletion fails
      }

      // Delete from database
      _ <- photoRepository.deletePhoto(photoId)

    } yield {
      logger.info(s"Successfully deleted photo $photoId by user $userId",
        "photoId", photoId.toString,
        "deletedBy", userId.toString)
    }
  }

  def updatePhotoPrimary(photoId: UUID, isPrimary: Boolean, userId: UUID): Future[com.tuachotu.model.response.UpdatePhotoPrimaryResponse] = {
    logger.debug("PhotoService.updatePhotoPrimary - Starting",
      "photoId", photoId.toString,
      "isPrimary", isPrimary,
      "userId", userId.toString)

    for {
      // Get the photo to verify it exists
      photoOpt <- photoRepository.findPhotoById(photoId)
      photo <- photoOpt match {
        case Some(p) =>
          logger.debug("PhotoService.updatePhotoPrimary - Photo found",
            "photoId", photoId.toString,
            "homeItemId", p.homeItemId.map(_.toString).getOrElse("null"),
            "homeId", p.homeId.map(_.toString).getOrElse("null"),
            "userId", p.userId.map(_.toString).getOrElse("null"),
            "currentIsPrimary", p.isPrimary)
          Future.successful(p)
        case None =>
          logger.debug("PhotoService.updatePhotoPrimary - Photo not found",
            "photoId", photoId.toString)
          Future.failed(new RuntimeException("Photo not found"))
      }

      // Check authorization: user must own the home where this photo belongs
      _ <- {
        logger.debug("PhotoService.updatePhotoPrimary - Checking authorization",
          "photoId", photoId.toString,
          "userId", userId.toString)
        checkPhotoDeleteAuthorization(photo, userId).map { _ =>
          logger.debug("PhotoService.updatePhotoPrimary - Authorization passed",
            "photoId", photoId.toString,
            "userId", userId.toString)
        }
      }

      // If setting as primary, clear other photos in the same context
      _ <- if (isPrimary) {
        // Determine context and clear other primary photos
        if (photo.homeItemId.isDefined) {
          logger.debug("PhotoService.updatePhotoPrimary - Clearing primary for home item context",
            "photoId", photoId.toString,
            "homeItemId", photo.homeItemId.get.toString)
          photoRepository.clearPrimaryForHomeItem(photo.homeItemId.get, photoId)
        } else if (photo.homeId.isDefined) {
          logger.debug("PhotoService.updatePhotoPrimary - Clearing primary for home context",
            "photoId", photoId.toString,
            "homeId", photo.homeId.get.toString)
          photoRepository.clearPrimaryForHome(photo.homeId.get, photoId)
        } else if (photo.userId.isDefined) {
          logger.debug("PhotoService.updatePhotoPrimary - Clearing primary for user context",
            "photoId", photoId.toString,
            "userId", photo.userId.get.toString)
          photoRepository.clearPrimaryForUser(photo.userId.get, photoId)
        } else {
          logger.debug("PhotoService.updatePhotoPrimary - No context found, skipping clear",
            "photoId", photoId.toString)
          Future.successful(0) // No context to clear
        }
      } else {
        logger.debug("PhotoService.updatePhotoPrimary - Not setting as primary, skipping clear",
          "photoId", photoId.toString)
        Future.successful(0) // Not setting as primary, no need to clear others
      }

      // Update this photo's primary status
      _ <- {
        logger.debug("PhotoService.updatePhotoPrimary - Updating photo primary status",
          "photoId", photoId.toString,
          "isPrimary", isPrimary)
        photoRepository.updatePhotoPrimary(photoId, isPrimary)
      }

      // Fetch updated photo for response
      updatedPhotoOpt <- photoRepository.findPhotoById(photoId)
      updatedPhoto <- updatedPhotoOpt match {
        case Some(p) =>
          logger.debug("PhotoService.updatePhotoPrimary - Photo updated successfully",
            "photoId", photoId.toString,
            "newIsPrimary", p.isPrimary)
          Future.successful(p)
        case None =>
          logger.error("PhotoService.updatePhotoPrimary - Photo not found after update",
            "photoId", photoId.toString)
          Future.failed(new RuntimeException("Photo not found after update"))
      }

    } yield {
      logger.info(s"Updated photo $photoId primary status to $isPrimary",
        "photoId", photoId.toString,
        "isPrimary", isPrimary,
        "userId", userId.toString)

      logger.debug("PhotoService.updatePhotoPrimary - Creating response",
        "photoId", photoId.toString,
        "homeItemId", updatedPhoto.homeItemId.map(_.toString).getOrElse("null"),
        "fileName", updatedPhoto.fileName.getOrElse("unknown"),
        "isPrimary", updatedPhoto.isPrimary)

      com.tuachotu.model.response.UpdatePhotoPrimaryResponse(
        id = updatedPhoto.id.toString,
        homeItemId = updatedPhoto.homeItemId.map(_.toString),
        fileName = updatedPhoto.fileName.getOrElse("unknown"),
        isPrimary = updatedPhoto.isPrimary,
        message = "Photo primary status updated successfully"
      )
    }
  }

  private def checkPhotoDeleteAuthorization(photo: Photo, userId: UUID): Future[Unit] = {
    // For photos associated with home items, check if user owns the home
    if (photo.homeItemId.isDefined) {
      photoRepository.checkUserOwnsPhotoViaHomeItem(photo.homeItemId.get, userId).map { hasAccess =>
        if (!hasAccess) {
          throw new RuntimeException("Photo access denied - user does not own the home")
        }
      }
    }
    // For photos associated directly with homes, check if user owns the home
    else if (photo.homeId.isDefined) {
      photoRepository.checkUserOwnsPhotoViaHome(photo.homeId.get, userId).map { hasAccess =>
        if (!hasAccess) {
          throw new RuntimeException("Photo access denied - user does not own the home")
        }
      }
    }
    // For user photos, check if it's the same user
    else if (photo.userId.isDefined) {
      if (photo.userId.get == userId) {
        Future.successful(())
      } else {
        Future.failed(new RuntimeException("Photo access denied - not your photo"))
      }
    }
    // For orphaned photos, only allow deletion by the creator
    else {
      if (photo.createdBy.contains(userId)) {
        Future.successful(())
      } else {
        Future.failed(new RuntimeException("Photo access denied - you did not create this photo"))
      }
    }
  }

  private def deleteFromS3(photo: Photo): Future[Unit] = {
    // Determine the S3 key based on context
    val s3Key = if (photo.homeItemId.isDefined) {
      s"${photo.homeItemId.get}/${photo.s3Key}"
    } else if (photo.homeId.isDefined) {
      s"${photo.homeId.get}/${photo.s3Key}"
    } else if (photo.userId.isDefined) {
      s"${photo.userId.get}/${photo.s3Key}"
    } else {
      photo.s3Key // Legacy photos without context
    }

    s3Service.deleteFile(s3Key)
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
            `type` = HomeItemType.toString(homeItem.itemType)
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