package com.tuachotu.service

import com.tuachotu.model.db.HomeWithOwnership
import com.tuachotu.model.response.{HomeResponse, HomeStatsResponse}
import com.tuachotu.repository.HomeRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

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

  private def convertToHomeResponse(homeWithOwnership: HomeWithOwnership): HomeResponse = {
    val home = homeWithOwnership.home
    
    HomeResponse(
      id = home.id.toString,
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