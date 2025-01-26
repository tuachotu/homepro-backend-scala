package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{UserService, UserRoleService}
import com.tuachotu.repository.{UserRepository, UserRoleRepository, RoleRepository}
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil}
import com.tuachotu.util.LoggerUtil.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class UserController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  private val userRepository = new UserRepository()
  private val userService = new UserService(userRepository)
  private val roleRepository = new RoleRepository()
  private val userRoleRepository = new UserRoleRepository()
  private val userRoleService = new UserRoleService(userRoleRepository, roleRepository)

  def userLoginRoute: Route = {
    path("api" / "users" / "login") {
      get {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            val result = for {
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(error) => Future.failed(new Exception(error))
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]
              userOpt <- userService.findByFirebaseId(firebaseId)
              user <- userOpt match {
                case Some(user) => Future.successful(user)
                case None => Future.failed(new Exception("User not found"))
              }
              roles <- userRoleService.getRoleNamesByUserId(user.id)
            } yield {
              val userName = user.name.getOrElse("NoName")
              HttpResponse(
                status = StatusCodes.OK,
                entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"$userName: ${roles.mkString(", ")}")
              )
            }

            onComplete(result) {
              case Success(response) => complete(response)
              case Failure(exception) =>
                val errorStatus = exception.getMessage match {
                  case "User not found" => StatusCodes.NotFound
                  case "Invalid token" => StatusCodes.Unauthorized
                  case _ => StatusCodes.InternalServerError
                }
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, exception.getMessage)
                ))
            }

          case _ =>
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Missing or invalid Authorization header")
            ))
        }
      }
    }
  }

  def routes: Route = userLoginRoute
}