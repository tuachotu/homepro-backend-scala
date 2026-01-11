package com.tuachotu.service

import com.tuachotu.model.db.{Note, NoteType}
import com.tuachotu.model.request.{CreateNoteRequest, UpdateNoteRequest}
import com.tuachotu.model.response.NoteResponse
import com.tuachotu.repository.NoteRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.util.HomeOwnerException

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class UnauthorizedNoteAccessException(message: String = "You do not have permission to perform this action on this note")
  extends HomeOwnerException(message)

class InvalidNoteRequestException(message: String)
  extends HomeOwnerException(message)

class NoteNotFoundException(noteId: String = "Note not found")
  extends HomeOwnerException(s"Note with ID $noteId not found")

class NoteService(
  noteRepository: NoteRepository,
  homeService: HomeService,
  homeItemRepository: com.tuachotu.repository.HomeItemRepository,
  userRoleService: UserRoleService
)(implicit ec: ExecutionContext) {

  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  /**
   * Check if user is authorized to access a home.
   * Returns true if user is a homeowner OR has expert role.
   */
  private def checkAuthorization(userId: UUID, homeId: UUID): Future[Boolean] = {
    for {
      // Check if user is a homeowner of this home
      isHomeowner <- homeService.checkUserAccess(userId, homeId)

      // Check if user has expert role
      roleNames <- userRoleService.getRoleNamesByUserId(userId)
      isExpert = roleNames.contains("expert")

    } yield {
      val isAuthorized = isHomeowner || isExpert
      logger.info("Authorization check",
        "userId", userId.toString,
        "homeId", homeId.toString,
        "isHomeowner", isHomeowner,
        "isExpert", isExpert,
        "isAuthorized", isAuthorized)
      isAuthorized
    }
  }

  /**
   * Create a new note.
   * Authorization: User must be homeowner OR expert.
   */
  def createNote(request: CreateNoteRequest, userId: UUID): Future[NoteResponse] = {
    // Validate exactly one of homeId or homeItemId is provided
    val (homeIdOpt, homeItemIdOpt) = (
      request.homeId.flatMap(id => Try(UUID.fromString(id)).toOption),
      request.homeItemId.flatMap(id => Try(UUID.fromString(id)).toOption)
    )

    (homeIdOpt, homeItemIdOpt) match {
      case (Some(homeId), None) =>
        // Note for home
        createNoteForHome(homeId, None, request, userId)

      case (None, Some(homeItemId)) =>
        // Note for home item - need to get homeId first
        for {
          homeItemOpt <- homeItemRepository.findItemById(homeItemId)
          homeItem <- homeItemOpt match {
            case Some(item) => Future.successful(item)
            case None => Future.failed(new InvalidNoteRequestException("Home item not found"))
          }
          response <- createNoteForHome(homeItem.homeId, Some(homeItemId), request, userId)
        } yield response

      case (Some(_), Some(_)) =>
        Future.failed(new InvalidNoteRequestException("Provide exactly one of homeId or homeItemId, not both"))

      case (None, None) =>
        Future.failed(new InvalidNoteRequestException("Either homeId or homeItemId must be provided"))
    }
  }

  private def createNoteForHome(
    homeId: UUID,
    homeItemId: Option[UUID],
    request: CreateNoteRequest,
    userId: UUID
  ): Future[NoteResponse] = {
    for {
      // Check authorization
      isAuthorized <- checkAuthorization(userId, homeId)
      _ <- if (isAuthorized) {
        Future.successful(())
      } else {
        Future.failed(new UnauthorizedNoteAccessException())
      }

      // Validate note type
      noteType <- Try(NoteType.fromString(request.noteType)).toOption match {
        case Some(nt) => Future.successful(nt)
        case None => Future.failed(new InvalidNoteRequestException(s"Invalid note type: ${request.noteType}"))
      }

      // Create note
      noteId = UUID.randomUUID()
      now = LocalDateTime.now()
      note = Note(
        id = noteId,
        homeId = if (homeItemId.isDefined) None else Some(homeId),
        homeItemId = homeItemId,
        title = request.title,
        body = request.body,
        noteType = noteType,
        isPinned = false,
        authorId = userId,
        createdBy = userId,
        createdAt = now,
        updatedBy = None,
        updatedAt = now,
        deletedAt = None
      )

      createdNote <- noteRepository.createNote(note)

    } yield {
      logger.info("Note created successfully",
        "noteId", noteId.toString,
        "homeId", homeId.toString,
        "homeItemId", homeItemId.map(_.toString).getOrElse("null"),
        "authorId", userId.toString,
        "noteType", request.noteType)
      NoteResponse.fromNote(createdNote)
    }
  }

  /**
   * Update a note.
   * Authorization: User must be homeowner OR expert.
   */
  def updateNote(noteId: UUID, request: UpdateNoteRequest, userId: UUID): Future[NoteResponse] = {
    for {
      // Get the note
      noteOpt <- noteRepository.findNoteById(noteId)
      note <- noteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

      // Get homeId for authorization check
      homeId <- note.homeId match {
        case Some(hId) => Future.successful(hId)
        case None =>
          // Note is associated with home item, need to fetch it
          note.homeItemId match {
            case Some(hiId) =>
              homeItemRepository.findItemById(hiId).flatMap {
                case Some(item) => Future.successful(item.homeId)
                case None => Future.failed(new RuntimeException("Home item not found"))
              }
            case None => Future.failed(new RuntimeException("Note has no associated home or home item"))
          }
      }

      // Check authorization
      isAuthorized <- checkAuthorization(userId, homeId)
      _ <- if (isAuthorized) {
        Future.successful(())
      } else {
        Future.failed(new UnauthorizedNoteAccessException())
      }

      // Validate note type if provided
      _ <- request.noteType match {
        case Some(nt) =>
          Try(NoteType.fromString(nt)).toOption match {
            case Some(_) => Future.successful(())
            case None => Future.failed(new InvalidNoteRequestException(s"Invalid note type: $nt"))
          }
        case None => Future.successful(())
      }

      // Update note
      _ <- noteRepository.updateNote(noteId, request.title, request.body, request.noteType, userId)

      // Fetch updated note
      updatedNoteOpt <- noteRepository.findNoteById(noteId)
      updatedNote <- updatedNoteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

    } yield {
      logger.info("Note updated successfully",
        "noteId", noteId.toString,
        "updatedBy", userId.toString)
      NoteResponse.fromNote(updatedNote)
    }
  }

  /**
   * Pin a note (idempotent).
   * Authorization: User must be homeowner OR expert.
   */
  def pinNote(noteId: UUID, userId: UUID): Future[NoteResponse] = {
    for {
      // Get the note
      noteOpt <- noteRepository.findNoteById(noteId)
      note <- noteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

      // Get homeId for authorization check
      homeId <- note.homeId match {
        case Some(hId) => Future.successful(hId)
        case None =>
          // Note is associated with home item, need to fetch it
          note.homeItemId match {
            case Some(hiId) =>
              homeItemRepository.findItemById(hiId).flatMap {
                case Some(item) => Future.successful(item.homeId)
                case None => Future.failed(new RuntimeException("Home item not found"))
              }
            case None => Future.failed(new RuntimeException("Note has no associated home or home item"))
          }
      }

      // Check authorization
      isAuthorized <- checkAuthorization(userId, homeId)
      _ <- if (isAuthorized) {
        Future.successful(())
      } else {
        Future.failed(new UnauthorizedNoteAccessException())
      }

      // Pin note (idempotent - no-op if already pinned)
      _ <- noteRepository.pinNote(noteId, userId)

      // Fetch updated note
      updatedNoteOpt <- noteRepository.findNoteById(noteId)
      updatedNote <- updatedNoteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

    } yield {
      logger.info("Note pinned",
        "noteId", noteId.toString,
        "userId", userId.toString,
        "wasPinned", note.isPinned)
      NoteResponse.fromNote(updatedNote)
    }
  }

  /**
   * Unpin a note (idempotent).
   * Authorization: User must be homeowner OR expert.
   */
  def unpinNote(noteId: UUID, userId: UUID): Future[NoteResponse] = {
    for {
      // Get the note
      noteOpt <- noteRepository.findNoteById(noteId)
      note <- noteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

      // Get homeId for authorization check
      homeId <- note.homeId match {
        case Some(hId) => Future.successful(hId)
        case None =>
          // Note is associated with home item, need to fetch it
          note.homeItemId match {
            case Some(hiId) =>
              homeItemRepository.findItemById(hiId).flatMap {
                case Some(item) => Future.successful(item.homeId)
                case None => Future.failed(new RuntimeException("Home item not found"))
              }
            case None => Future.failed(new RuntimeException("Note has no associated home or home item"))
          }
      }

      // Check authorization
      isAuthorized <- checkAuthorization(userId, homeId)
      _ <- if (isAuthorized) {
        Future.successful(())
      } else {
        Future.failed(new UnauthorizedNoteAccessException())
      }

      // Unpin note (idempotent - no-op if already unpinned)
      _ <- noteRepository.unpinNote(noteId, userId)

      // Fetch updated note
      updatedNoteOpt <- noteRepository.findNoteById(noteId)
      updatedNote <- updatedNoteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

    } yield {
      logger.info("Note unpinned",
        "noteId", noteId.toString,
        "userId", userId.toString,
        "wasPinned", note.isPinned)
      NoteResponse.fromNote(updatedNote)
    }
  }

  /**
   * Soft delete a note.
   * Authorization: User must be homeowner OR expert.
   */
  def deleteNote(noteId: UUID, userId: UUID): Future[Unit] = {
    for {
      // Get the note
      noteOpt <- noteRepository.findNoteById(noteId)
      note <- noteOpt match {
        case Some(n) => Future.successful(n)
        case None => Future.failed(new NoteNotFoundException(noteId.toString))
      }

      // Get homeId for authorization check
      homeId <- note.homeId match {
        case Some(hId) => Future.successful(hId)
        case None =>
          // Note is associated with home item, need to fetch it
          note.homeItemId match {
            case Some(hiId) =>
              homeItemRepository.findItemById(hiId).flatMap {
                case Some(item) => Future.successful(item.homeId)
                case None => Future.failed(new RuntimeException("Home item not found"))
              }
            case None => Future.failed(new RuntimeException("Note has no associated home or home item"))
          }
      }

      // Check authorization
      isAuthorized <- checkAuthorization(userId, homeId)
      _ <- if (isAuthorized) {
        Future.successful(())
      } else {
        Future.failed(new UnauthorizedNoteAccessException())
      }

      // Soft delete
      rowsAffected <- noteRepository.softDeleteNote(noteId, userId)
      _ <- if (rowsAffected > 0) {
        Future.successful(())
      } else {
        Future.failed(new NoteNotFoundException(noteId.toString))
      }

    } yield {
      logger.info("Note deleted",
        "noteId", noteId.toString,
        "deletedBy", userId.toString)
    }
  }

  /**
   * Get notes by homeId or homeItemId.
   * Authorization: User must be homeowner OR expert.
   */
  def getNotes(homeId: Option[UUID], homeItemId: Option[UUID], userId: UUID): Future[List[NoteResponse]] = {
    (homeId, homeItemId) match {
      case (Some(hId), None) =>
        // Get notes for home
        for {
          // Check authorization
          isAuthorized <- checkAuthorization(userId, hId)
          _ <- if (isAuthorized) {
            Future.successful(())
          } else {
            Future.failed(new UnauthorizedNoteAccessException())
          }

          notes <- noteRepository.findNotesByHomeId(hId)
        } yield notes.map(NoteResponse.fromNote)

      case (None, Some(hiId)) =>
        // Get notes for home item
        for {
          // Get home item to find homeId
          homeItemOpt <- homeItemRepository.findItemById(hiId)
          homeItem <- homeItemOpt match {
            case Some(item) => Future.successful(item)
            case None => Future.failed(new InvalidNoteRequestException("Home item not found"))
          }

          // Check authorization
          isAuthorized <- checkAuthorization(userId, homeItem.homeId)
          _ <- if (isAuthorized) {
            Future.successful(())
          } else {
            Future.failed(new UnauthorizedNoteAccessException())
          }

          notes <- noteRepository.findNotesByHomeItemId(hiId)
        } yield notes.map(NoteResponse.fromNote)

      case (Some(_), Some(_)) =>
        Future.failed(new InvalidNoteRequestException("Provide exactly one of homeId or homeItemId, not both"))

      case (None, None) =>
        Future.failed(new InvalidNoteRequestException("Either homeId or homeItemId must be provided"))
    }
  }
}
