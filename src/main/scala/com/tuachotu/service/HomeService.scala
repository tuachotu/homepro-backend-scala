package com.tuachotu.service

import com.tuachotu.model.db.{Home, HomeWithOwnership}
import com.tuachotu.model.request.AddHomeRequest
import com.tuachotu.model.response.{AddHomeResponse, HomeResponse, HomeStatsResponse}
import com.tuachotu.repository.HomeRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import spray.json.DefaultJsonProtocol._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class HomeService(
  homeRepository: HomeRepository
)(implicit ec: ExecutionContext) {
  
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def getHomesByUserId(userId: UUID): Future[List[HomeResponse]] = {
    for {
      homesWithOwnership <- homeRepository.findHomesByUserId(userId)
    } yield {
      val homeResponses = homesWithOwnership.map(convertToHomeResponse)
      logger.info(s"Retrieved ${homeResponses.length} homes for user ID: $userId")
      homeResponses
    }
  }

  def checkUserAccess(userId: UUID, homeId: UUID): Future[Boolean] = {
    homeRepository.checkUserHomeAccess(userId, homeId).map(_.isDefined)
  }

  def getUserRole(userId: UUID, homeId: UUID): Future[Option[String]] = {
    homeRepository.checkUserHomeAccess(userId, homeId)
  }

  def createHome(addHomeRequest: AddHomeRequest, userId: UUID): Future[AddHomeResponse] = {
    for {
      // Check if user already has a home (one home restriction)
      existingHomeCount <- homeRepository.countHomesByUserId(userId)
      _ <- if (existingHomeCount > 0) {
        Future.failed(new IllegalStateException("User already has a home. Only one home per user is currently allowed."))
      } else {
        Future.successful(())
      }
      
      // Create the home
      homeId = UUID.randomUUID()
      now = LocalDateTime.now()
      
      // Convert metadata Map to JSON string if provided
      metadataJson = addHomeRequest.metadata match {
        case Some(metadata) => metadata.toJson.compactPrint
        case None => "{}"
      }
      
      home = Home(
        id = homeId,
        address = addHomeRequest.address,
        name = Some(addHomeRequest.name),
        isPrimary = true, // First home is always primary
        metadata = metadataJson,
        createdAt = now,
        createdBy = userId,
        updatedAt = now,
        updatedBy = Some(userId)
      )
      
      // Create home and ownership in sequence
      createdHome <- homeRepository.createHome(home)
      _ <- homeRepository.createHomeOwnership(homeId, userId, "owner")
      
    } yield {
      logger.info(s"Successfully created home for user $userId", 
        "homeId", homeId.toString,
        "homeName", addHomeRequest.name,
        "address", addHomeRequest.address.getOrElse("not_provided"))
      AddHomeResponse.fromDbModel(createdHome)
    }
  }

  private def convertToHomeResponse(homeWithOwnership: HomeWithOwnership): HomeResponse = {
    val home = homeWithOwnership.home
    
    HomeResponse(
      id = home.id.toString,
      name = home.name,
      address = home.address,
      role = homeWithOwnership.userRole,
      created_at = home.createdAt.format(dateFormatter),
      updated_at = home.updatedAt.format(dateFormatter),
      stats = HomeStatsResponse(
        total_items = homeWithOwnership.totalItems,
        total_photos = homeWithOwnership.totalPhotos,
        emergency_items = homeWithOwnership.emergencyItems
      )
    )
  }
}