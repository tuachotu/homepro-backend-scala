package com.tuachotu.http.core
import io.netty.handler.codec.http.{DefaultFullHttpResponse, FullHttpRequest, HttpMethod, HttpResponse}

trait Route {
  def path: String
  def method: HttpMethod
  def handle(request: FullHttpRequest): DefaultFullHttpResponse
}

object RouteRegistry {
  private var routes: List[Route] = List()
  def addRoute(route: Route): Unit = {
    routes = route :: routes
  }
  def getRoutes: List[Route] = routes
}
