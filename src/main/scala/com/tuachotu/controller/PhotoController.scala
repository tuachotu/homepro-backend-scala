package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{PhotoService, S3Service}
import com.tuachotu.repository.PhotoRepository
import com.tuachotu.model.response.PhotoResponseProtocol
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PhotoController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val photoRepository = new PhotoRepository()
  private val s3Service = new S3Service()
  private val photoService = new PhotoService(photoRepository, s3Service)

  import PhotoResponseProtocol._

  def photosRoute: Route = cors() {
    path("api" / "photos") {
      get {
        parameters("homeId".optional, "homeItemId".optional) { (homeIdOpt, homeItemIdOpt) =>
          
          // Validate that at least one parameter is provided
          (homeIdOpt, homeItemIdOpt) match {
            case (None, None) =>
              complete(HttpResponse(
                status = StatusCodes.BadRequest,
                entity = HttpEntity(ContentTypes.`application/json`, 
                  """{"error": "At least one of homeId or homeItemId must be provided"}""")
              ))
              
            case (Some(homeIdStr), None) =>
              Try(UUID.fromString(homeIdStr)) match {
                case Success(homeId) =>
                  val result = photoService.getPhotosByHomeId(homeId)
                  onComplete(result) {
                    case Success(photos) =>
                      logger.info(s"Retrieved ${photos.length} photos for home ID: $homeId")
                      complete(HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(ContentTypes.`application/json`, photos.toJson.compactPrint)
                      ))
                    case Failure(exception) =>
                      logger.error(s"Failed to retrieve photos for home ID: $homeId", exception)
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(ContentTypes.`application/json`, 
                          s"""{"error": "Failed to retrieve photos: ${exception.getMessage}"}""")
                      ))
                  }
                case Failure(_) =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      """{"error": "Invalid homeId format. Must be a valid UUID"}""")
                  ))
              }
              
            case (None, Some(homeItemIdStr)) =>
              Try(UUID.fromString(homeItemIdStr)) match {
                case Success(homeItemId) =>
                  val result = photoService.getPhotosByHomeItemId(homeItemId)
                  onComplete(result) {
                    case Success(photos) =>
                      logger.info(s"Retrieved ${photos.length} photos for home item ID: $homeItemId")
                      complete(HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(ContentTypes.`application/json`, photos.toJson.compactPrint)
                      ))
                    case Failure(exception) =>
                      logger.error(s"Failed to retrieve photos for home item ID: $homeItemId", exception)
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(ContentTypes.`application/json`, 
                          s"""{"error": "Failed to retrieve photos: ${exception.getMessage}"}""")
                      ))
                  }
                case Failure(_) =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      """{"error": "Invalid homeItemId format. Must be a valid UUID"}""")
                  ))
              }
              
            case (Some(_), Some(_)) =>
              complete(HttpResponse(
                status = StatusCodes.BadRequest,
                entity = HttpEntity(ContentTypes.`application/json`, 
                  """{"error": "Only one of homeId or homeItemId should be provided, not both"}""")
              ))
          }
        }
      }
    }
  }

  def routes: Route = photosRoute
}