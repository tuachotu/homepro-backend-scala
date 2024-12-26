package com.tuachotu.http

// Core HTTP server and routing logic

import com.tuachotu.http.core.{HttpServer, Route, RouteRegistry}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

// Netty HTTP utilities
import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpVersion, HttpResponseStatus, HttpHeaderNames, HttpMethod, HttpResponse}

// Byte manipulation (optional, for response content)
import io.netty.buffer.Unpooled

object Main extends App {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  RouteRegistry.addRoute(new Route {
    override def path: String = "/hello"
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