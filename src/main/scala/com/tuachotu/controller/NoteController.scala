package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes
import com.tuachotu.service.{NoteService, HomeService, UserService, UserRoleService}
import com.tuachotu.repository.{NoteRepository, HomeRepository, UserRepository, HomeItemRepository, UserRoleRepository, RoleRepository}
import com.tuachotu.model.request.{CreateNoteRequest, CreateNoteRequestJsonProtocol, UpdateNoteRequest, UpdateNoteRequestJsonProtocol}
import com.tuachotu.model.response.NoteResponseProtocol
import com.tuachotu.util.{FirebaseAuthHandler, LoggerUtil, UnauthorizedAccessException, UserNotFoundException}
import com.tuachotu.util.LoggerUtil.Logger
import spray.json._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.model.HttpOriginMatcher
import akka.http.scaladsl.model.headers.HttpOrigin

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class NoteController()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  private val noteRepository = new NoteRepository()
  private val homeRepository = new HomeRepository()
  private val userRepository = new UserRepository()
  private val homeItemRepository = new HomeItemRepository()
  private val userRoleRepository = new UserRoleRepository()
  private val roleRepository = new RoleRepository()

  private val homeService = new HomeService(homeRepository)
  private val userService = new UserService(userRepository)
  private val userRoleService = new UserRoleService(userRoleRepository, roleRepository)
  private val noteService = new NoteService(noteRepository, homeService, homeItemRepository, userRoleService)

  import CreateNoteRequestJsonProtocol._
  import UpdateNoteRequestJsonProtocol._
  import NoteResponseProtocol._

  // Define CORS settings with allowed origins
  val corsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowedOrigins(
      HttpOriginMatcher(
        HttpOrigin("http://localhost:3000"),
        HttpOrigin("https://home-owners.tech")
      )
    )
    .withAllowCredentials(true)

  def createNoteRoute: Route = cors(corsSettings) {
    path("api" / "notes") {
      post {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            entity(as[String]) { requestBody =>

              logger.info("POST /api/notes request received",
                "hasAuthToken", "true",
                "requestBodyLength", requestBody.length)

              Try(requestBody.parseJson.convertTo[CreateNoteRequest]) match {
                case Success(createNoteRequest) =>
                  val result = for {
                    // Validate Firebase token
                    claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                      case Right(claims) => Future.successful(claims)
                      case Left(_) => Future.failed(new UnauthorizedAccessException)
                    }
                    firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

                    // Get user from Firebase ID
                    requestingUserOpt <- userService.findByFirebaseId(firebaseId)
                    requestingUser <- requestingUserOpt match {
                      case Some(user) => Future.successful(user)
                      case None => Future.failed(new UserNotFoundException("User not found"))
                    }

                    // Create the note (authorization checked in service)
                    response <- noteService.createNote(createNoteRequest, requestingUser.id)
                  } yield {
                    val responseJson = response.toJson.compactPrint
                    logger.info("POST /api/notes - Success response",
                      "userId", requestingUser.id.toString,
                      "noteId", response.id,
                      "status", StatusCodes.Created.intValue,
                      "responseSize", responseJson.length)
                    HttpResponse(
                      status = StatusCodes.Created,
                      entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                    )
                  }

                  onComplete(result) {
                    case Success(response) => complete(response)
                    case Failure(exception) =>
                      val errorStatus = exception match {
                        case _: UserNotFoundException => StatusCodes.NotFound
                        case _: UnauthorizedAccessException => StatusCodes.Forbidden
                        case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                        case ex: com.tuachotu.service.InvalidNoteRequestException => StatusCodes.BadRequest
                        case _ => StatusCodes.InternalServerError
                      }
                      val errorMsg = exception.getMessage
                      logger.error("POST /api/notes - Error response",
                        exception,
                        "error", errorMsg,
                        "status", errorStatus.intValue)
                      complete(HttpResponse(
                        status = errorStatus,
                        entity = HttpEntity(ContentTypes.`application/json`,
                          Map("error" -> errorMsg).toJson.compactPrint)
                      ))
                  }

                case Failure(exception) =>
                  val errorMsg = s"Invalid JSON format: ${exception.getMessage}"
                  logger.error("POST /api/notes - Bad Request (Invalid JSON)",
                    exception,
                    "error", errorMsg,
                    "status", StatusCodes.BadRequest.intValue)
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`,
                      Map("error" -> errorMsg).toJson.compactPrint)
                  ))
              }
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("POST /api/notes - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def updateNoteRoute: Route = cors(corsSettings) {
    path("api" / "notes" / JavaUUID) { noteId =>
      put {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            entity(as[String]) { requestBody =>

              logger.info("PUT /api/notes/{noteId} request received",
                "noteId", noteId.toString,
                "hasAuthToken", "true",
                "requestBodyLength", requestBody.length)

              Try(requestBody.parseJson.convertTo[UpdateNoteRequest]) match {
                case Success(updateNoteRequest) =>
                  val result = for {
                    // Validate Firebase token
                    claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                      case Right(claims) => Future.successful(claims)
                      case Left(_) => Future.failed(new UnauthorizedAccessException)
                    }
                    firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

                    // Get user from Firebase ID
                    requestingUserOpt <- userService.findByFirebaseId(firebaseId)
                    requestingUser <- requestingUserOpt match {
                      case Some(user) => Future.successful(user)
                      case None => Future.failed(new UserNotFoundException("User not found"))
                    }

                    // Update the note (authorization checked in service)
                    response <- noteService.updateNote(noteId, updateNoteRequest, requestingUser.id)
                  } yield {
                    val responseJson = response.toJson.compactPrint
                    logger.info("PUT /api/notes/{noteId} - Success response",
                      "noteId", noteId.toString,
                      "userId", requestingUser.id.toString,
                      "status", StatusCodes.OK.intValue)
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
                        case _: UnauthorizedAccessException => StatusCodes.Forbidden
                        case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                        case ex: com.tuachotu.service.InvalidNoteRequestException => StatusCodes.BadRequest
                        case ex: com.tuachotu.service.NoteNotFoundException => StatusCodes.NotFound
                        case _ => StatusCodes.InternalServerError
                      }
                      val errorMsg = exception.getMessage
                      logger.error("PUT /api/notes/{noteId} - Error response",
                        exception,
                        "noteId", noteId.toString,
                        "error", errorMsg,
                        "status", errorStatus.intValue)
                      complete(HttpResponse(
                        status = errorStatus,
                        entity = HttpEntity(ContentTypes.`application/json`,
                          Map("error" -> errorMsg).toJson.compactPrint)
                      ))
                  }

                case Failure(exception) =>
                  val errorMsg = s"Invalid JSON format: ${exception.getMessage}"
                  logger.error("PUT /api/notes/{noteId} - Bad Request (Invalid JSON)",
                    exception,
                    "noteId", noteId.toString,
                    "error", errorMsg,
                    "status", StatusCodes.BadRequest.intValue)
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`,
                      Map("error" -> errorMsg).toJson.compactPrint)
                  ))
              }
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("PUT /api/notes/{noteId} - Unauthorized (Missing auth)",
              "noteId", noteId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def pinNoteRoute: Route = cors(corsSettings) {
    path("api" / "notes" / JavaUUID / "pin") { noteId =>
      post {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)

            logger.info("POST /api/notes/{noteId}/pin request received",
              "noteId", noteId.toString,
              "hasAuthToken", "true")

            val result = for {
              // Validate Firebase token
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(_) => Future.failed(new UnauthorizedAccessException)
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

              // Get user from Firebase ID
              requestingUserOpt <- userService.findByFirebaseId(firebaseId)
              requestingUser <- requestingUserOpt match {
                case Some(user) => Future.successful(user)
                case None => Future.failed(new UserNotFoundException("User not found"))
              }

              // Pin the note (authorization checked in service)
              response <- noteService.pinNote(noteId, requestingUser.id)
            } yield {
              val responseJson = response.toJson.compactPrint
              logger.info("POST /api/notes/{noteId}/pin - Success response",
                "noteId", noteId.toString,
                "userId", requestingUser.id.toString,
                "status", StatusCodes.OK.intValue)
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
                  case _: UnauthorizedAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.NoteNotFoundException => StatusCodes.NotFound
                  case _ => StatusCodes.InternalServerError
                }
                val errorMsg = exception.getMessage
                logger.error("POST /api/notes/{noteId}/pin - Error response",
                  exception,
                  "noteId", noteId.toString,
                  "error", errorMsg,
                  "status", errorStatus.intValue)
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`,
                    Map("error" -> errorMsg).toJson.compactPrint)
                ))
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("POST /api/notes/{noteId}/pin - Unauthorized (Missing auth)",
              "noteId", noteId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def unpinNoteRoute: Route = cors(corsSettings) {
    path("api" / "notes" / JavaUUID / "unpin") { noteId =>
      post {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)

            logger.info("POST /api/notes/{noteId}/unpin request received",
              "noteId", noteId.toString,
              "hasAuthToken", "true")

            val result = for {
              // Validate Firebase token
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(_) => Future.failed(new UnauthorizedAccessException)
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

              // Get user from Firebase ID
              requestingUserOpt <- userService.findByFirebaseId(firebaseId)
              requestingUser <- requestingUserOpt match {
                case Some(user) => Future.successful(user)
                case None => Future.failed(new UserNotFoundException("User not found"))
              }

              // Unpin the note (authorization checked in service)
              response <- noteService.unpinNote(noteId, requestingUser.id)
            } yield {
              val responseJson = response.toJson.compactPrint
              logger.info("POST /api/notes/{noteId}/unpin - Success response",
                "noteId", noteId.toString,
                "userId", requestingUser.id.toString,
                "status", StatusCodes.OK.intValue)
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
                  case _: UnauthorizedAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.NoteNotFoundException => StatusCodes.NotFound
                  case _ => StatusCodes.InternalServerError
                }
                val errorMsg = exception.getMessage
                logger.error("POST /api/notes/{noteId}/unpin - Error response",
                  exception,
                  "noteId", noteId.toString,
                  "error", errorMsg,
                  "status", errorStatus.intValue)
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`,
                    Map("error" -> errorMsg).toJson.compactPrint)
                ))
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("POST /api/notes/{noteId}/unpin - Unauthorized (Missing auth)",
              "noteId", noteId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def deleteNoteRoute: Route = cors(corsSettings) {
    path("api" / "notes" / JavaUUID) { noteId =>
      delete {
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)

            logger.info("DELETE /api/notes/{noteId} request received",
              "noteId", noteId.toString,
              "hasAuthToken", "true")

            val result = for {
              // Validate Firebase token
              claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                case Right(claims) => Future.successful(claims)
                case Left(_) => Future.failed(new UnauthorizedAccessException)
              }
              firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

              // Get user from Firebase ID
              requestingUserOpt <- userService.findByFirebaseId(firebaseId)
              requestingUser <- requestingUserOpt match {
                case Some(user) => Future.successful(user)
                case None => Future.failed(new UserNotFoundException("User not found"))
              }

              // Delete the note (authorization checked in service)
              _ <- noteService.deleteNote(noteId, requestingUser.id)
            } yield {
              logger.info("DELETE /api/notes/{noteId} - Success response",
                "noteId", noteId.toString,
                "userId", requestingUser.id.toString,
                "status", StatusCodes.NoContent.intValue)
              HttpResponse(
                status = StatusCodes.NoContent,
                entity = HttpEntity.Empty
              )
            }

            onComplete(result) {
              case Success(response) => complete(response)
              case Failure(exception) =>
                val errorStatus = exception match {
                  case _: UserNotFoundException => StatusCodes.NotFound
                  case _: UnauthorizedAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                  case ex: com.tuachotu.service.NoteNotFoundException => StatusCodes.NotFound
                  case _ => StatusCodes.InternalServerError
                }
                val errorMsg = exception.getMessage
                logger.error("DELETE /api/notes/{noteId} - Error response",
                  exception,
                  "noteId", noteId.toString,
                  "error", errorMsg,
                  "status", errorStatus.intValue)
                complete(HttpResponse(
                  status = errorStatus,
                  entity = HttpEntity(ContentTypes.`application/json`,
                    Map("error" -> errorMsg).toJson.compactPrint)
                ))
            }

          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("DELETE /api/notes/{noteId} - Unauthorized (Missing auth)",
              "noteId", noteId.toString,
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`,
                Map("error" -> errorMsg).toJson.compactPrint)
            ))
        }
      }
    }
  }

  def getNotesRoute: Route = cors(corsSettings) {
    path("api" / "notes") {
      get {
        parameters("homeId".optional, "homeItemId".optional) { (homeIdParam, homeItemIdParam) =>
          optionalHeaderValueByName("Authorization") {
            case Some(authHeader) if authHeader.startsWith("Bearer ") =>
              val token = authHeader.substring(7)

              logger.info("GET /api/notes request received",
                "homeId", homeIdParam.getOrElse("null"),
                "homeItemId", homeItemIdParam.getOrElse("null"),
                "hasAuthToken", "true")

              val result = for {
                // Validate Firebase token
                claims <- FirebaseAuthHandler.validateTokenAsync(token).flatMap {
                  case Right(claims) => Future.successful(claims)
                  case Left(_) => Future.failed(new UnauthorizedAccessException)
                }
                firebaseId = claims.getOrElse("user_id", "").asInstanceOf[String]

                // Get user from Firebase ID
                requestingUserOpt <- userService.findByFirebaseId(firebaseId)
                requestingUser <- requestingUserOpt match {
                  case Some(user) => Future.successful(user)
                  case None => Future.failed(new UserNotFoundException("User not found"))
                }

                // Parse UUIDs
                homeIdOpt = homeIdParam.flatMap(id => Try(UUID.fromString(id)).toOption)
                homeItemIdOpt = homeItemIdParam.flatMap(id => Try(UUID.fromString(id)).toOption)

                // Get notes (authorization checked in service)
                notes <- noteService.getNotes(homeIdOpt, homeItemIdOpt, requestingUser.id)
              } yield {
                val responseJson = notes.toJson.compactPrint
                logger.info("GET /api/notes - Success response",
                  "userId", requestingUser.id.toString,
                  "noteCount", notes.length,
                  "status", StatusCodes.OK.intValue)
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
                    case _: UnauthorizedAccessException => StatusCodes.Forbidden
                    case ex: com.tuachotu.service.UnauthorizedNoteAccessException => StatusCodes.Forbidden
                    case ex: com.tuachotu.service.InvalidNoteRequestException => StatusCodes.BadRequest
                    case _ => StatusCodes.InternalServerError
                  }
                  val errorMsg = exception.getMessage
                  logger.error("GET /api/notes - Error response",
                    exception,
                    "error", errorMsg,
                    "status", errorStatus.intValue)
                  complete(HttpResponse(
                    status = errorStatus,
                    entity = HttpEntity(ContentTypes.`application/json`,
                      Map("error" -> errorMsg).toJson.compactPrint)
                  ))
              }

            case _ =>
              val errorMsg = "Missing or invalid Authorization header"
              logger.error("GET /api/notes - Unauthorized (Missing auth)",
                "error", errorMsg,
                "status", StatusCodes.Unauthorized.intValue)
              complete(HttpResponse(
                status = StatusCodes.Unauthorized,
                entity = HttpEntity(ContentTypes.`application/json`,
                  Map("error" -> errorMsg).toJson.compactPrint)
              ))
          }
        }
      }
    }
  }

  def routes: Route = createNoteRoute ~ updateNoteRoute ~ pinNoteRoute ~ unpinNoteRoute ~ deleteNoteRoute ~ getNotesRoute
}
