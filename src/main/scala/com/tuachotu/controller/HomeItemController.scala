package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{HomeItemService, HomeService, UserService, S3Service}
import com.tuachotu.repository.{HomeItemRepository, HomeRepository, UserRepository}
import com.tuachotu.model.request.{AddHomeItemRequest, AddHomeItemRequestJsonProtocol}
import com.tuachotu.model.response.{AddHomeItemResponseProtocol}
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import akka.http.scaladsl.model.headers.HttpOrigin

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class HomeItemController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val homeRepository = new HomeRepository()
  private val homeItemRepository = new HomeItemRepository()
  private val userRepository = new UserRepository()
  private val s3Service = new S3Service()
  
  private val homeService = new HomeService(homeRepository)
  private val homeItemService = new HomeItemService(homeItemRepository, s3Service)
  private val userService = new UserService(userRepository)

  import AddHomeItemRequestJsonProtocol._
  import AddHomeItemResponseProtocol._

  // Define CORS settings with allowed origins
  val corsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowedOrigins(
      HttpOriginMatcher(
        HttpOrigin("http://localhost:3000"),
        HttpOrigin("https://home-owners.tech")
      )
    )
    .withAllowCredentials(true)

  def createHomeItemRoute: Route = cors(corsSettings) {
    path("api" / "homes" / JavaUUID / "items") { homeId =>
      post {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            entity(as[String]) { requestBody =>
              
              // Log incoming request
              logger.info("POST /api/homes/{homeId}/items request received",
                "homeId", homeId.toString,
                "hasAuthToken", "true",
                "requestBodyLength", requestBody.length)
              
              Try(requestBody.parseJson.convertTo[AddHomeItemRequest]) match {
                case Success(addHomeItemRequest) =>
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
                    
                    // Check if user has access to this home
                    hasAccess <- homeService.checkUserAccess(requestingUser.id, homeId)
                    _ <- if (hasAccess) {
                      Future.successful(())
                    } else {
                      Future.failed(new UnauthorizedAccessException)
                    }
                    
                    // Create the home item
                    response <- homeItemService.createHomeItem(
                      homeId, addHomeItemRequest, requestingUser.id
                    )
                  } yield {
                    val responseJson = response.toJson.compactPrint
                    logger.info("POST /api/homes/{homeId}/items - Success response",
                      "homeId", homeId.toString,
                      "userId", requestingUser.id.toString,
                      "itemId", response.id,
                      "itemName", response.name,
                      "status", StatusCodes.Created.intValue,
                      "responseSize", responseJson.length)
                    HttpResponse(
                      status = StatusCodes.Created,
                      entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                    )
                  }

                  onComplete(result) {
                    case Success(response) => complete(response)
                    case Failure(exception) =>
                      val errorStatus = exception match {
                        case _: UserNotFoundException => StatusCodes.NotFound
                        case _: UnauthorizedAccessException => StatusCodes.Forbidden
                        case ex if ex.getMessage.contains("Invalid item type") => StatusCodes.BadRequest
                        case _ => StatusCodes.InternalServerError
                      }
                      val errorMsg = exception.getMessage
                      logger.error("POST /api/homes/{homeId}/items - Error response", 
                        exception,
                        "homeId", homeId.toString,
                        "error", errorMsg,
                        "status", errorStatus.intValue)
                      complete(HttpResponse(
                        status = errorStatus,
                        entity = HttpEntity(ContentTypes.`application/json`, 
                          Map("error" -> errorMsg).toJson.compactPrint)
                      ))
                  }
                  
                case Failure(exception) =>
                  val errorMsg = s"Invalid JSON format: ${exception.getMessage}"
                  logger.error("POST /api/homes/{homeId}/items - Bad Request (Invalid JSON)",
                    exception,
                    "homeId", homeId.toString,
                    "error", errorMsg,
                    "status", StatusCodes.BadRequest.intValue)
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      Map("error" -> errorMsg).toJson.compactPrint)
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("POST /api/homes/{homeId}/items - Unauthorized (Missing auth)",
              "homeId", homeId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def deleteHomeItemRoute: Route = cors(corsSettings) {
    path("api" / "homes" / JavaUUID / "items" / JavaUUID) { (homeId, itemId) =>
      delete {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)

            // Log incoming request
            logger.info("DELETE /api/homes/{homeId}/items/{itemId} request received",
              "homeId", homeId.toString,
              "itemId", itemId.toString,
              "hasAuthToken", "true")

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

              // Check if user has access to this home
              hasAccess <- homeService.checkUserAccess(requestingUser.id, homeId)
              _ <- if (hasAccess) {
                Future.successful(())
              } else {
                Future.failed(new UnauthorizedAccessException)
              }

              // Delete the home item (cascading deletes handled in service)
              _ <- homeItemService.deleteHomeItem(homeId, itemId, requestingUser.id)
            } yield {
              logger.info("DELETE /api/homes/{homeId}/items/{itemId} - Success response",
                "homeId", homeId.toString,
                "itemId", itemId.toString,
                "userId", requestingUser.id.toString,
                "status", StatusCodes.NoContent.intValue)
              HttpResponse(
                status = StatusCodes.NoContent,
                entity = HttpEntity.Empty
              )
            }

            onComplete(result) {
              case Success(response) => complete(response)
              case Failure(exception) =>
                val errorStatus = exception match {
                  case _: UserNotFoundException => StatusCodes.NotFound
                  case _: UnauthorizedAccessException => StatusCodes.Forbidden
                  case ex if ex.getMessage.contains("not found") => StatusCodes.NotFound
                  case _ => StatusCodes.InternalServerError
                }
                val errorMsg = exception.getMessage
                logger.error("DELETE /api/homes/{homeId}/items/{itemId} - Error response",
                  exception,
                  "homeId", homeId.toString,
                  "itemId", itemId.toString,
                  "error", errorMsg,
                  "status", errorStatus.intValue)
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`,
                    Map("error" -> errorMsg).toJson.compactPrint)
                ))
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("DELETE /api/homes/{homeId}/items/{itemId} - Unauthorized (Missing auth)",
              "homeId", homeId.toString,
              "itemId", itemId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def routes: Route = createHomeItemRoute ~ deleteHomeItemRoute
}