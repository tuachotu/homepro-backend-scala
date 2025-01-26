package com.tuachotu

import com.tuachotu.controller.UserController
import com.tuachotu.repository.UserRepository
import com.tuachotu.service.UserService
import com.tuachotu.util.{ConfigUtil, FirebaseAuthHandler, LoggerUtil}
import com.tuachotu.util.LoggerUtil.Logger

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{CommandLineParser, Failure, Success, Try}

// Provide a given instance for Array[String]
// This is needed to pass args - to main
// TODO: Clean it up?
given CommandLineParser.FromString[Array[String]] with {
  def fromString(s: String): Array[String] = s.split(",")
}
object HomeProMain {
  @main def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("homepro-system")
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    implicit  val logger: Logger = LoggerUtil.getLogger(getClass)
    // Add all the routes here
    val userController = new UserController()
    // Combine all user-related routes
    val routes = userController.routes
    Http().newServerAt("0.0.0.0", ConfigUtil.getInt("server.port", 2107)).bind(routes)
    Await.result(system.whenTerminated, Duration.Inf)
  }
}