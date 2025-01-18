package com.tuachotu.http
import com.tuachotu.http.handlers.controller.UserController
import com.tuachotu.http.core.{HttpServer, Route, RouteRegistry}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.util.FirebaseAuthHandler
import com.tuachotu.service.UserService
import com.tuachotu.repository.UserRepository

import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpVersion, HttpResponseStatus, HttpHeaderNames, HttpMethod, HttpResponse}
import io.netty.buffer.Unpooled
import scala.util.CommandLineParser
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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
    RouteRegistry.addRoute(new UserController().UserLoginRoute())
    RouteRegistry.getRoutes.foreach(route =>
    LoggerUtil.info("Route registered", "path", route.path, "method", route.method.toString))
    HttpServer.start()
  }
}