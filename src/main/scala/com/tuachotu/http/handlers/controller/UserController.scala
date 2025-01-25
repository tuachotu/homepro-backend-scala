package com.tuachotu.http.handlers.controller

import com.tuachotu.http.core.{HttpServer, Route, RouteRegistry}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.util.FirebaseAuthHandler
import com.tuachotu.service.{UserService, UserRoleService}
import com.tuachotu.repository._

import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpVersion, HttpResponseStatus, HttpHeaderNames, HttpMethod, HttpResponse}
import io.netty.buffer.Unpooled
import scala.util.CommandLineParser
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._


class UserController {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  val userRepository = new UserRepository()
  val userService = new UserService(userRepository)
  val RoleRepository = new RoleRepository()
  val userRoleRepository = new UserRoleRepository()
  val userRoleService = new UserRoleService(userRoleRepository, RoleRepository)

  def UserLoginRoute(): Route = {
    new Route {
      override def path: String = "/api/users/login"

      override def method: HttpMethod = HttpMethod.GET

      override def handle(request: FullHttpRequest): DefaultFullHttpResponse = {
        // Extract token from Authorization header
        val authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION)
        val token = Option(authHeader).filter(_.startsWith("Bearer ")).map(_.substring(7))
        token match {
          case Some(t) =>
            FirebaseAuthHandler.validateToken(t) match {
              // Token is valid, process the request
              case Right(claims) =>
                // Await result of findAll with a timeout of 10 seconds
                Try {
                  Await.result(userService.findByFirebaseId(claims.getOrElse("user_id", "").asInstanceOf[String]), 10.seconds)
                } match {
                  case Success(user) =>
                    val usersName  = user.map(_.name).getOrElse("NoName")
                    val userId = user.map(_.id).getOrElse(UUID.randomUUID())
                    val roles = Await.result(userRoleService.getRoleNamesByUserId(userId).recover {
                      case ex: Exception =>
                        LoggerUtil.error("UserLoginRoute", "error",  ex.getMessage)
                        Seq.empty[String]
                    }, 10.seconds)
                    val content = Unpooled.copiedBuffer(roles.mkString(",").getBytes())
                    val response = new DefaultFullHttpResponse(
                      HttpVersion.HTTP_1_1,
                      HttpResponseStatus.OK,
                      content
                    )
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                    response
                  case Failure(exception) =>
                    // DB Read failed
                    val content = Unpooled.copiedBuffer("cccc".getBytes())
                    val response = new DefaultFullHttpResponse(
                      HttpVersion.HTTP_1_1,
                      HttpResponseStatus.UNAUTHORIZED,
                      content
                    )
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                    response
                }
              case Left(error) => // Authentication Failed
                // Token is invalid, respond with 401
                val content = Unpooled.copiedBuffer(error.getBytes())
                val response = new DefaultFullHttpResponse(
                  HttpVersion.HTTP_1_1,
                  HttpResponseStatus.UNAUTHORIZED,
                  content
                )
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                response
            }

          case None =>
            // No token provided, respond with 401
            val content = Unpooled.copiedBuffer("Missing Authorization header".getBytes())
            val response = new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.UNAUTHORIZED,
              content
            )
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
            response
        }
      }
    }
  }

}
