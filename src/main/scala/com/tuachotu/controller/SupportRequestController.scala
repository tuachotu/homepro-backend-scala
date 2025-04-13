package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.SupportRequestService
import com.tuachotu.repository.SupportRequestRepository
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.model.response.{UserLoginResponseProtocol, UserLoginResponse}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import akka.http.scaladsl.model.headers.HttpOrigin
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}

//implicit val stringMapFormat: RootJsonFormat[Map[String, String]] = mapFormat[String, String]

class SupportRequestController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val supportRequestRepository = new SupportRequestRepository()
  private val supportRequestService = new SupportRequestService(supportRequestRepository)

  // JSON formatter for error map

  import spray.json.DefaultJsonProtocol._

  implicit val stringMapFormat: RootJsonFormat[Map[String, String]] = mapFormat[String, String]

  //TODO: Change to support request response
  //import UserLoginResponseProtocol._

  // Define CORS settings with allowed origins
  val corsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowedOrigins(
      HttpOriginMatcher(
        HttpOrigin("http://localhost:3000"),
        HttpOrigin("https://home-owners.tech")
      )
    )
    .withAllowCredentials(true)

  def supportRequestRoute: Route = cors(corsSettings) {
    path("api" / "support-requests" ) {
      get {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            val result = for {
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(_) => Future.failed(new UnauthorizedAccessException)
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]
//              userOpt <- userService.findByFirebaseId(firebaseId)
//              user <- userOpt match {
//                case Some(user) => Future.successful(user)
//                case None => Future.failed(new UserNotFoundException("User not found"))
//              }
//              roles <- userRoleService.getRoleNamesByUserId(user.id)
            } yield {
              val response = firebaseId
              HttpResponse(
                status = StatusCodes.OK,
                entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, response)
//                entity = HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint)
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
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> exception.getMessage).toJson.compactPrint)
                ))
            }

          case _ =>
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, """{"error": "Missing or invalid Authorization header"}""")
            ))
        }
      }
    }
  }

  def routes: Route = supportRequestRoute
}
