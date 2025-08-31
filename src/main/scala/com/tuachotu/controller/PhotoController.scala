package com.tuachotu.controller

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.{StatusCodes, Multipart}
import com.tuachotu.service.{PhotoService, S3Service, UserService}
import com.tuachotu.repository.{PhotoRepository, UserRepository}
import com.tuachotu.model.response.{PhotoResponse, PhotoResponseProtocol, PhotoUploadResponse, PhotoUploadResponseProtocol}
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
import akka.stream.Materializer

class PhotoController()(implicit ec: ExecutionContext, mat: Materializer) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)
  
  private val photoRepository = new PhotoRepository()
  private val userRepository = new UserRepository()
  private val s3Service = new S3Service()
  private val photoService = new PhotoService(photoRepository, s3Service)
  private val userService = new UserService(userRepository)

  import PhotoResponseProtocol._
  import PhotoUploadResponseProtocol._

  // Define CORS settings with allowed origins
  val corsSettings: CorsSettings = CorsSettings.defaultSettings
    .withAllowedOrigins(
      HttpOriginMatcher(
        HttpOrigin("http://localhost:3000"),
        HttpOrigin("https://home-owners.tech")
      )
    )
    .withAllowCredentials(true)

  def photosRoute: Route = cors(corsSettings) {
    path("api" / "photos") {
      get {
        // Log incoming request
        logger.info("GET /api/photos request received")
        
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            
            parameters("homeId".optional, "homeItemId".optional) { (homeIdOpt, homeItemIdOpt) =>
              
              logger.info("GET /api/photos - Processing with auth token", 
                "homeId", homeIdOpt.getOrElse("not_provided"),
                "homeItemId", homeItemIdOpt.getOrElse("not_provided"))
              
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
                
                // Process photos based on parameters
                photos <- processPhotoRequest(homeIdOpt, homeItemIdOpt, requestingUser.id)
              } yield photos
              
              onComplete(result) {
                case Success(photos) =>
                  val responseJson = photos.toJson.compactPrint
                  logger.info("GET /api/photos - Success response", 
                    "photoCount", photos.length,
                    "status", StatusCodes.OK.intValue,
                    "responseSize", responseJson.length)
                  complete(HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(ContentTypes.`application/json`, responseJson)
                  ))
                case Failure(exception) =>
                  val errorStatus = exception match {
                    case _: UserNotFoundException => StatusCodes.NotFound
                    case _: UnauthorizedAccessException => StatusCodes.Unauthorized
                    case _: IllegalArgumentException => StatusCodes.BadRequest
                    case _ => StatusCodes.InternalServerError
                  }
                  val errorMsg = exception.getMessage
                  logger.error("GET /api/photos - Error response", 
                    exception,
                    "error", errorMsg,
                    "status", errorStatus.intValue)
                  complete(HttpResponse(
                    status = errorStatus,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      s"""{\"error\": \"$errorMsg\"}""")
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("GET /api/photos - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                s"""{\"error\": \"$errorMsg\"}""")
            ))
        }
      } ~
      post {
        // Photo upload endpoint
        optionalHeaderValueByName("Authorization") {
          case Some(authHeader) if authHeader.startsWith("Bearer ") =>
            val token = authHeader.substring(7)
            
            parameters("homeItemId") { homeItemIdStr =>
              
              logger.info("POST /api/photos request received", 
                "homeItemId", homeItemIdStr,
                "hasAuthToken", "true")
              
              Try(UUID.fromString(homeItemIdStr)) match {
                case Success(homeItemId) =>
                  entity(as[Multipart.FormData]) { formData =>
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
                      
                      // Process the photo upload
                      uploadResult <- processPhotoUpload(formData, homeItemId, requestingUser.id)
                    } yield uploadResult

                    onComplete(result) {
                      case Success(response) =>
                        logger.info("POST /api/photos - Success response",
                          "homeItemId", homeItemId.toString,
                          "status", StatusCodes.Created.intValue)
                        complete(HttpResponse(
                          status = StatusCodes.Created,
                          entity = HttpEntity(ContentTypes.`application/json`, response.toJson.compactPrint)
                        ))
                      case Failure(exception) =>
                        val errorStatus = exception match {
                          case _: UserNotFoundException => StatusCodes.NotFound
                          case _: UnauthorizedAccessException => StatusCodes.Unauthorized
                          case _: IllegalArgumentException => StatusCodes.BadRequest
                          case _ => StatusCodes.InternalServerError
                        }
                        val errorMsg = exception.getMessage
                        logger.error("POST /api/photos - Error response", 
                          exception,
                          "homeItemId", homeItemId.toString,
                          "error", errorMsg,
                          "status", errorStatus.intValue)
                        complete(HttpResponse(
                          status = errorStatus,
                          entity = HttpEntity(ContentTypes.`application/json`, 
                            s"""{\"error\": \"$errorMsg\"}""")
                        ))
                    }
                  }
                case Failure(_) =>
                  val errorMsg = "Invalid homeItemId format. Must be a valid UUID"
                  logger.error("POST /api/photos - Bad Request (Invalid UUID)",
                    "homeItemIdStr", homeItemIdStr,
                    "error", errorMsg,
                    "status", StatusCodes.BadRequest.intValue)
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(ContentTypes.`application/json`, 
                      s"""{\"error\": \"$errorMsg\"}""")
                  ))
              }
            }
            
          case _ =>
            val errorMsg = "Missing or invalid Authorization header"
            logger.error("POST /api/photos - Unauthorized (Missing auth)",
              "error", errorMsg,
              "status", StatusCodes.Unauthorized.intValue)
            complete(HttpResponse(
              status = StatusCodes.Unauthorized,
              entity = HttpEntity(ContentTypes.`application/json`, 
                s"""{\"error\": \"$errorMsg\"}""")
            ))
        }
      }
    }
  }
  
  private def processPhotoRequest(homeIdOpt: Option[String], homeItemIdOpt: Option[String], userId: UUID): Future[List[PhotoResponse]] = {
    // Validate that at least one parameter is provided
    (homeIdOpt, homeItemIdOpt) match {
      case (None, None) =>
        Future.failed(new IllegalArgumentException("At least one of homeId or homeItemId must be provided"))
        
      case (Some(homeIdStr), None) =>
        Try(UUID.fromString(homeIdStr)) match {
          case Success(homeId) =>
            logger.info("GET /api/photos - Processing home photos request", "homeId", homeId.toString)
            photoService.getPhotosByHomeId(homeId)
          case Failure(_) =>
            Future.failed(new IllegalArgumentException("Invalid homeId format. Must be a valid UUID"))
        }
        
      case (None, Some(homeItemIdStr)) =>
        Try(UUID.fromString(homeItemIdStr)) match {
          case Success(homeItemId) =>
            logger.info("GET /api/photos - Processing home item photos request", "homeItemId", homeItemId.toString)
            photoService.getPhotosByHomeItemId(homeItemId)
          case Failure(_) =>
            Future.failed(new IllegalArgumentException("Invalid homeItemId format. Must be a valid UUID"))
        }
        
      case (Some(_), Some(_)) =>
        Future.failed(new IllegalArgumentException("Only one of homeId or homeItemId should be provided, not both"))
    }
  }

  private def processPhotoUpload(formData: Multipart.FormData, homeItemId: UUID, userId: UUID): Future[PhotoUploadResponse] = {
    import akka.stream.scaladsl.Sink
    
    formData.parts.mapAsync(1) { part =>
      part.name match {
        case "photo" =>
          // Extract file information
          val filename = part.filename.getOrElse("photo.jpg")
          val contentType = part.entity.contentType.mediaType.toString()
          
          // Get the file data
          part.entity.dataBytes.runWith(Sink.fold(akka.util.ByteString.empty)(_ ++ _)).map { data =>
            Some((filename, contentType, data.toArray))
          }
        case _ =>
          part.entity.discardBytes()
          Future.successful(None)
      }
    }.runWith(Sink.headOption).flatMap {
      case Some(Some((filename, contentType, data))) =>
        // Upload to S3 first
        val s3Key = s"$homeItemId/$filename"
        
        for {
          // Upload to S3
          _ <- s3Service.uploadFile(s3Key, data, Some(contentType))
          
          // Create photo record in database
          photoId = UUID.randomUUID()
          photo = com.tuachotu.model.db.Photo(
            id = photoId,
            homeId = None, // Not set when linking to home item
            homeItemId = Some(homeItemId), // Primary association
            userId = None, // Don't set userId when homeItemId is set
            s3Key = filename, // Store just filename as per context-based architecture
            fileName = Some(filename),
            contentType = Some(contentType),
            caption = None,
            isPrimary = false,
            createdBy = Some(userId),
            createdAt = java.time.LocalDateTime.now()
          )
          
          // Save to database
          _ <- savePhotoToDatabase(photo)
          
          // Generate presigned URL for response
          photoUrl <- s3Service.generatePresignedUrlForContext(homeItemId.toString, filename)
          
        } yield PhotoUploadResponse(
          id = photoId.toString,
          homeItemId = homeItemId.toString,
          fileName = filename,
          s3Key = s3Key,
          contentType = Some(contentType),
          caption = None,
          message = "Photo uploaded successfully",
          photoUrl = Some(photoUrl)
        )
      case _ =>
        Future.failed(new IllegalArgumentException("No photo file found in request"))
    }
  }

  private def savePhotoToDatabase(photo: com.tuachotu.model.db.Photo): Future[Unit] = {
    val sql = """
      INSERT INTO photos (id, home_id, home_item_id, user_id, s3_key, file_name, content_type, 
                         caption, is_primary, created_by, created_at) 
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    import com.tuachotu.db.DatabaseConnection
    import java.sql.Timestamp
    
    val params = List(
      photo.id,
      photo.homeId.orNull,
      photo.homeItemId.orNull,
      photo.userId.orNull,
      photo.s3Key,
      photo.fileName.orNull,
      photo.contentType.orNull,
      photo.caption.orNull,
      photo.isPrimary,
      photo.createdBy.orNull,
      Timestamp.valueOf(photo.createdAt)
    )

    DatabaseConnection.executeUpdate(sql, params*).map { rowsAffected =>
      if (rowsAffected == 0) {
        throw new RuntimeException("Failed to save photo to database")
      }
      logger.info(s"Successfully saved photo ${photo.id} to database")
    }
  }

  def routes: Route = photosRoute
}