package com.tuachotu.http

import com.tuachotu.http.core.{HttpServer, Route, RouteRegistry}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.util.FirebaseAuthHandler
import com.tuachotu.repository.UserRepository

import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpVersion, HttpResponseStatus, HttpHeaderNames, HttpMethod, HttpResponse}
import io.netty.buffer.Unpooled
import scala.util.CommandLineParser
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

// Provide a given instance for Array[String]
given CommandLineParser.FromString[Array[String]] with {
  def fromString(s: String): Array[String] = s.split(",")
}
object HomeProMain {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  @main def main(args: Array[String]): Unit = {
    RouteRegistry.addRoute(new Route {
      override def path: String = "/api/hello"

      override def method: HttpMethod = HttpMethod.GET

      override def handle(request: FullHttpRequest): DefaultFullHttpResponse = {
        // Extract token from Authorization header
        val authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION)
        val token = Option(authHeader).filter(_.startsWith("Bearer ")).map(_.substring(7))
        token match {
          case Some(t) =>
            FirebaseAuthHandler.validateToken(t) match {
              case Right(claims) => // Authentication Passed
                try {
                  // Await result of findAll with a timeout of 10 seconds
                  val users = Await.result(new UserRepository().findAll(), 10.seconds)
                  println(users) // Print the list of users
                } catch {
                  case exception: Exception =>
                    println(s"Error fetching users: ${exception.getMessage}")
                }
                // Token is valid, process the request
                val content = Unpooled.copiedBuffer("Hello, World!".getBytes())
                val response = new DefaultFullHttpResponse(
                  HttpVersion.HTTP_1_1,
                  HttpResponseStatus.OK,
                  content
                )
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
                response

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
    })
    LoggerUtil.info("Route registered", "path", "/hello", "method", "GET")
    HttpServer.start()
  }
}