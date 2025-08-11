package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{PhotoService, S3Service, UserService}
import com.tuachotu.repository.{PhotoRepository, UserRepository}
import com.tuachotu.model.response.{PhotoResponse, PhotoResponseProtocol}
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class PhotoController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val photoRepository = new PhotoRepository()
  private val userRepository = new UserRepository()
  private val s3Service = new S3Service()
  private val photoService = new PhotoService(photoRepository, s3Service)
  private val userService = new UserService(userRepository)

  import PhotoResponseProtocol._

  def photosRoute: Route = cors() {
    path("api" / "photos") {
      get {
        // Log incoming request
        logger.info("GET /api/photos request received")
        
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            
            parameters("homeId".optional, "homeItemId".optional) { (homeIdOpt, homeItemIdOpt) =>
              
              logger.info("GET /api/photos - Processing with auth token", 
                "homeId", homeIdOpt.getOrElse("not_provided"),
                "homeItemId", homeItemIdOpt.getOrElse("not_provided"))
              
              val result = for {
                // Validate Firebase token
                claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                  case Right(claims) => Future.successful(claims)
                  case Left(_) => Future.failed(new UnauthorizedAccessException)
                }
                firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]
                
                // Get user from Firebase ID
                requestingUserOpt <- userService.findByFirebaseId(firebaseId)
                requestingUser <- requestingUserOpt match {
                  case Some(user) => Future.successful(user)
                  case None => Future.failed(new UserNotFoundException("User not found"))
                }
                
                // Process photos based on parameters
                photos <- processPhotoRequest(homeIdOpt, homeItemIdOpt, requestingUser.id)
              } yield photos
              
              onComplete(result) {
                case Success(photos) =>
                  val responseJson = photos.toJson.compactPrint
                  logger.info("GET /api/photos - Success response", 
                    "photoCount", photos.length,
                    "status", StatusCodes.OK.intValue,
                    "responseSize", responseJson.length)
                  complete(HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                  ))
                case Failure(exception) =>
                  val errorStatus = exception match {
                    case _: UserNotFoundException => StatusCodes.NotFound
                    case _: UnauthorizedAccessException => StatusCodes.Unauthorized
                    case _: IllegalArgumentException => StatusCodes.BadRequest
                    case _ => StatusCodes.InternalServerError
                  }
                  val errorMsg = exception.getMessage
                  logger.error("GET /api/photos - Error response", 
                    exception,
                    "error", errorMsg,
                    "status", errorStatus.intValue)
                  complete(HttpResponse(
                    status = errorStatus,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      s"""{\"error\": \"$errorMsg\"}""")
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("GET /api/photos - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                s"""{\"error\": \"$errorMsg\"}""")
            ))
        }
      }
    }
  }
  
  private def processPhotoRequest(homeIdOpt: Option[String], homeItemIdOpt: Option[String], userId: UUID): Future[List[PhotoResponse]] = {
    // Validate that at least one parameter is provided
    (homeIdOpt, homeItemIdOpt) match {
      case (None, None) =>
        Future.failed(new IllegalArgumentException("At least one of homeId or homeItemId must be provided"))
        
      case (Some(homeIdStr), None) =>
        Try(UUID.fromString(homeIdStr)) match {
          case Success(homeId) =>
            logger.info("GET /api/photos - Processing home photos request", "homeId", homeId.toString)
            photoService.getPhotosByHomeId(homeId)
          case Failure(_) =>
            Future.failed(new IllegalArgumentException("Invalid homeId format. Must be a valid UUID"))
        }
        
      case (None, Some(homeItemIdStr)) =>
        Try(UUID.fromString(homeItemIdStr)) match {
          case Success(homeItemId) =>
            logger.info("GET /api/photos - Processing home item photos request", "homeItemId", homeItemId.toString)
            photoService.getPhotosByHomeItemId(homeItemId)
          case Failure(_) =>
            Future.failed(new IllegalArgumentException("Invalid homeItemId format. Must be a valid UUID"))
        }
        
      case (Some(_), Some(_)) =>
        Future.failed(new IllegalArgumentException("Only one of homeId or homeItemId should be provided, not both"))
    }
  }

  def routes: Route = photosRoute
}