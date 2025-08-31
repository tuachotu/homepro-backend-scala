package com.tuachotu.service

import com.tuachotu.model.db.{HomeItem, HomeItemEnhanced, HomeItemType}
import com.tuachotu.model.request.AddHomeItemRequest
import com.tuachotu.model.response.{AddHomeItemResponse, HomeItemResponse}
import com.tuachotu.repository.HomeItemRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

class HomeItemService(
  homeItemRepository: HomeItemRepository,
  s3Service: S3Service
)(implicit ec: ExecutionContext) {
  
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def createHomeItem(
    homeId: UUID,
    request: AddHomeItemRequest,
    userId: UUID
  ): Future[AddHomeItemResponse] = {
    try {
      val itemType = HomeItemType.fromString(request.itemType)
      val itemId = UUID.randomUUID()
      val now = LocalDateTime.now()
      
      val dataJson = request.data.getOrElse("{}")
      
      val homeItem = HomeItem(
        id = itemId,
        homeId = homeId,
        name = request.name,
        itemType = itemType,
        isEmergency = request.isEmergency,
        data = dataJson,
        createdBy = Some(userId),
        createdAt = now
      )

      homeItemRepository.createHomeItem(homeItem).map { createdItem =>
        logger.info(s"Successfully created home item: ${createdItem.id} for home: $homeId")
        AddHomeItemResponse.fromHomeItem(createdItem)
      }
    } catch {
      case ex: IllegalArgumentException =>
        logger.error(s"Invalid item type: ${request.itemType}", ex)
        Future.failed(new RuntimeException(s"Invalid item type: ${request.itemType}"))
      case ex: Exception =>
        logger.error(s"Failed to create home item for home: $homeId", ex)
        Future.failed(ex)
    }
  }

  def getHomeItems(
    homeId: UUID,
    userId: UUID,
    itemType: Option[String] = None,
    emergency: Option[Boolean] = None,
    limit: Int = 50,
    offset: Int = 0
  ): Future[List[HomeItemResponse]] = {
    for {
      homeItems <- homeItemRepository.findItemsByHomeId(homeId, itemType, emergency, limit, offset)
      homeItemResponses <- convertToHomeItemResponses(homeItems)
    } yield {
      logger.info(s"Retrieved ${homeItemResponses.length} items for home ID: $homeId")
      homeItemResponses
    }
  }

  def getHomeItemById(itemId: UUID, userId: UUID): Future[Option[HomeItemResponse]] = {
    for {
      homeItemOpt <- homeItemRepository.findItemById(itemId)
      responseOpt <- homeItemOpt match {
        case Some(item) => convertToHomeItemResponses(List(item)).map(_.headOption)
        case None => Future.successful(None)
      }
    } yield responseOpt
  }

  private def convertToHomeItemResponses(homeItems: List[HomeItemEnhanced]): Future[List[HomeItemResponse]] = {
    Future.traverse(homeItems) { item =>
      val primaryPhotoUrlFuture = item.primaryS3Key match {
        case Some(s3Key) => s3Service.generatePresignedUrlForContext(item.id.toString, s3Key).map(Some(_))
        case None => Future.successful(None)
      }

      for {
        primaryPhotoUrl <- primaryPhotoUrlFuture
      } yield {
        HomeItemResponse(
          id = item.id.toString,
          name = item.name,
          `type` = item.itemType,
          is_emergency = item.isEmergency,
          data = parseJsonbData(item.data),
          created_at = item.createdAt.format(dateFormatter),
          photo_count = item.photoCount,
          primary_photo_url = primaryPhotoUrl
        )
      }
    }
  }

  private def parseJsonbData(jsonbString: String): Map[String, Any] = {
    Try {
      jsonbString.parseJson match {
        case JsObject(fields) => 
          fields.map { case (key, value) => 
            key -> extractJsonValue(value)
          }.toMap
        case _ => Map.empty[String, Any]
      }
    } match {
      case Success(result) => result
      case Failure(exception) =>
        logger.warn(s"Failed to parse JSONB data: $jsonbString", exception)
        Map.empty[String, Any]
    }
  }

  private def extractJsonValue(jsValue: JsValue): Any = jsValue match {
    case JsString(s) => s
    case JsNumber(n) => 
      if (n.isValidInt) n.toInt
      else if (n.isValidLong) n.toLong
      else n.toDouble
    case JsTrue => true
    case JsFalse => false
    case JsNull => null
    case JsArray(elements) => elements.map(extractJsonValue).toList
    case JsObject(fields) => fields.map { case (k, v) => k -> extractJsonValue(v) }.toMap
  }
}