package com.tuachotu.http

import com.tuachotu.http.core.{HttpServer, Route, RouteRegistry}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpVersion, HttpResponseStatus, HttpHeaderNames, HttpMethod, HttpResponse}
import io.netty.buffer.Unpooled
import scala.util.CommandLineParser

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
        val content = Unpooled.copiedBuffer("Hello, World!".getBytes())
        val response = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK,
          content
        )
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
        // TODO: Fix this method. It seems logging is not working
        // We have to use println
        //LoggerUtil.info("Route registered1111")
        println(response.toString)
        response
      }
    })
    LoggerUtil.info("Route registered", "path", "/hello", "method", "GET")
    HttpServer.start()
  }
}