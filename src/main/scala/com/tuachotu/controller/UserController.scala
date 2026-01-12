package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{UserService, UserRoleService}
import com.tuachotu.repository.{UserRepository, UserRoleRepository, RoleRepository}
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.model.response.{UserLoginResponseProtocol,UserLoginResponse}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import akka.http.scaladsl.model.headers.HttpOrigin
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._


class UserController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val userRepository = new UserRepository()
  private val userService = new UserService(userRepository)
  private val roleRepository = new RoleRepository()
  private val userRoleRepository = new UserRoleRepository()
  private val userRoleService = new UserRoleService(userRoleRepository, roleRepository)
  
  import UserLoginResponseProtocol._

  // Define CORS settings with allowed origins
  val corsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowedOrigins(
      HttpOriginMatcher(
        HttpOrigin("http://localhost:3000"),
        HttpOrigin("https://home-owners.tech")
      )
    )
    .withAllowCredentials(true)

  def userLoginRoute: Route = cors(corsSettings) {
    path("api" / "users" / "login") {
      get {
        // Log incoming request
        logger.info("GET /api/users/login request received")
        
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            logger.info("GET /api/users/login - Processing with auth token")
            val result = for {
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(_) => Future.failed(new UnauthorizedAccessException)
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]
              userOpt <- userService.findByFirebaseId(firebaseId)
              user <- userOpt match {
                case Some(user) =>
                  logger.info("Existing user found", "userId", user.id.toString, "firebaseUid", firebaseId)
                  Future.successful(user)
                case None =>
                  logger.info("New user detected, creating user", "firebaseUid", firebaseId)
                  for {
                    newUser <- userService.createFromFirebaseClaims(firebaseId, claims)
                    _ <- userRoleService.assignDefaultRole(newUser.id)
                  } yield newUser
              }
              roles <- userRoleService.getRoleNamesByUserId(user.id)
            } yield {
              val response = UserLoginResponse(user.id.toString, user.name.getOrElse("NoName"), roles.mkString(", "))
              val responseJson = response.toJson.compactPrint
              logger.info("GET /api/users/login - Success response",
                "userId", user.id.toString,
                "userName", user.name.getOrElse("NoName"),
                "roleCount", roles.length,
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
                logger.error("GET /api/users/login - Error response", 
                  exception,
                  "error", errorMsg,
                  "status", errorStatus.intValue)
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> errorMsg).toJson.compactPrint)
                ))
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("GET /api/users/login - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, s"""{"error": "$errorMsg"}""")
            ))
        }
      }
    }
  }

  def routes: Route = userLoginRoute
}