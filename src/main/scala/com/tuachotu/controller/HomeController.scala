package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{HomeService, HomeItemService, UserService, S3Service}
import com.tuachotu.repository.{HomeRepository, HomeItemRepository, UserRepository}
import com.tuachotu.model.response.HomeResponseProtocol
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class HomeController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val homeRepository = new HomeRepository()
  private val homeItemRepository = new HomeItemRepository()
  private val userRepository = new UserRepository()
  private val s3Service = new S3Service()
  
  private val homeService = new HomeService(homeRepository)
  private val homeItemService = new HomeItemService(homeItemRepository, s3Service)
  private val userService = new UserService(userRepository)

  import HomeResponseProtocol._

  def homesRoute: Route = cors() {
    path("api" / "homes") {
      get {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            parameters("userId".optional) { userIdOpt =>
              
              // Log incoming request
              logger.info("GET /api/homes request received",
                "userId", userIdOpt.getOrElse("not_provided"),
                "hasAuthToken", "true")
              
              userIdOpt match {
                case Some(userIdStr) =>
                  Try(UUID.fromString(userIdStr)) match {
                    case Success(userId) =>
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
                        
                        // Check if requesting user can access the requested user's homes
                        // For now, users can only see their own homes
                        _ <- if (requestingUser.id == userId) {
                          Future.successful(())
                        } else {
                          Future.failed(new UnauthorizedAccessException)
                        }
                        
                        // Get homes for the user
                        homes <- homeService.getHomesByUserId(userId)
                      } yield {
                        val responseJson = homes.toJson.compactPrint
                        logger.info("GET /api/homes - Success response",
                          "userId", userId.toString,
                          "homeCount", homes.length,
                          "status", StatusCodes.OK.intValue,
                          "responseSize", responseJson.length)
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                        )
                      }

                      onComplete(result) {
                        case Success(response) => complete(response)
                        case Failure(exception) =>
                          val errorStatus = exception match {
                            case _: UserNotFoundException => StatusCodes.NotFound
                            case _: UnauthorizedAccessException => StatusCodes.Unauthorized
                            case _ => StatusCodes.InternalServerError
                          }
                          val errorMsg = exception.getMessage
                          logger.error("GET /api/homes - Error response", 
                            exception,
                            "userId", userId.toString,
                            "error", errorMsg,
                            "status", errorStatus.intValue)
                          complete(HttpResponse(
                            status = errorStatus,
                            entity = HttpEntity(ContentTypes.`application/json`, 
                              Map("error" -> errorMsg).toJson.compactPrint)
                          ))
                      }
                      
                    case Failure(_) =>
                      val errorMsg = "Invalid userId format. Must be a valid UUID"
                      logger.error("GET /api/homes - Bad Request (Invalid UUID)",
                        "userIdStr", userIdStr,
                        "error", errorMsg,
                        "status", StatusCodes.BadRequest.intValue)
                      complete(HttpResponse(
                        status = StatusCodes.BadRequest,
                        entity = HttpEntity(ContentTypes.`application/json`, 
                          s"""{"error": "$errorMsg"}""")
                      ))
                  }
                  
                case None =>
                  val errorMsg = "userId parameter is required"
                  logger.error("GET /api/homes - Bad Request (Missing userId)",
                    "error", errorMsg,
                    "status", StatusCodes.BadRequest.intValue)
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      s"""{"error": "$errorMsg"}""")
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("GET /api/homes - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                s"""{"error": "$errorMsg"}""")
            ))
        }
      }
    }
  }

  def homeItemsRoute: Route = cors() {
    path("api" / "homes" / JavaUUID / "items") { homeId =>
      get {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            parameters(
              "type".optional,
              "emergency".as[Boolean].optional,
              "limit".as[Int] ? 50,
              "offset".as[Int] ? 0
            ) { (itemType, emergency, limit, offset) =>
              
              // Log incoming request
              logger.info("GET /api/homes/{homeId}/items request received",
                "homeId", homeId.toString,
                "type", itemType.getOrElse("not_provided"),
                "emergency", emergency.getOrElse("not_provided").toString,
                "limit", limit,
                "offset", offset,
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
                
                // Get home items
                homeItems <- homeItemService.getHomeItems(
                  homeId, requestingUser.id, itemType, emergency, limit, offset
                )
              } yield {
                val responseJson = homeItems.toJson.compactPrint
                logger.info("GET /api/homes/{homeId}/items - Success response",
                  "homeId", homeId.toString,
                  "userId", requestingUser.id.toString,
                  "itemCount", homeItems.length,
                  "status", StatusCodes.OK.intValue,
                  "responseSize", responseJson.length)
                HttpResponse(
                  status = StatusCodes.OK,
                  entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                )
              }

              onComplete(result) {
                case Success(response) => complete(response)
                case Failure(exception) =>
                  val errorStatus = exception match {
                    case _: UserNotFoundException => StatusCodes.NotFound
                    case _: UnauthorizedAccessException => StatusCodes.Forbidden
                    case _ => StatusCodes.InternalServerError
                  }
                  val errorMsg = exception.getMessage
                  logger.error("GET /api/homes/{homeId}/items - Error response", 
                    exception,
                    "homeId", homeId.toString,
                    "error", errorMsg,
                    "status", errorStatus.intValue)
                  complete(HttpResponse(
                    status = errorStatus,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      Map("error" -> exception.getMessage).toJson.compactPrint)
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("GET /api/homes/{homeId}/items - Unauthorized (Missing auth)",
              "homeId", homeId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                s"""{"error": "$errorMsg"}""")
            ))
        }
      }
    }
  }

  def routes: Route = homesRoute ~ homeItemsRoute
}