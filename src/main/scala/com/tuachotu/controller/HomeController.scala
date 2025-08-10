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
                        HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(ContentTypes.`application/json`, homes.toJson.compactPrint)
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
                          logger.error(s"Error fetching homes for user $userId", exception)
                          complete(HttpResponse(
                            status = errorStatus,
                            entity = HttpEntity(ContentTypes.`application/json`, 
                              Map("error" -> exception.getMessage).toJson.compactPrint)
                          ))
                      }
                      
                    case Failure(_) =>
                      complete(HttpResponse(
                        status = StatusCodes.BadRequest,
                        entity = HttpEntity(ContentTypes.`application/json`, 
                          """{"error": "Invalid userId format. Must be a valid UUID"}""")
                      ))
                  }
                  
                case None =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      """{"error": "userId parameter is required"}""")
                  ))
              }
            }
            
          case _ =>
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                """{"error": "Missing or invalid Authorization header"}""")
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
                HttpResponse(
                  status = StatusCodes.OK,
                  entity = HttpEntity(ContentTypes.`application/json`, homeItems.toJson.compactPrint)
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
                  logger.error(s"Error fetching items for home $homeId", exception)
                  complete(HttpResponse(
                    status = errorStatus,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      Map("error" -> exception.getMessage).toJson.compactPrint)
                  ))
              }
            }
            
          case _ =>
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                """{"error": "Missing or invalid Authorization header"}""")
            ))
        }
      }
    }
  }

  def routes: Route = homesRoute ~ homeItemsRoute
}