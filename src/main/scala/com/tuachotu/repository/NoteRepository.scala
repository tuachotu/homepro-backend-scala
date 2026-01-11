package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{Note, NoteType}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.sql.{ResultSet, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class NoteRepository()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  def createNote(note: Note): Future[Note] = {
    val sql = """
      INSERT INTO notes (
        id, home_id, home_item_id, title, body, note_type,
        is_pinned, author_id, created_by, created_at, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    val params = List(
      note.id,
      note.homeId.orNull,
      note.homeItemId.orNull,
      note.title.orNull,
      note.body,
      NoteType.toString(note.noteType),
      note.isPinned,
      note.authorId,
      note.createdBy,
      Timestamp.valueOf(note.createdAt),
      Timestamp.valueOf(note.updatedAt)
    )

    DatabaseConnection.executeUpdate(sql, params*).map { rowsAffected =>
      if (rowsAffected > 0) {
        logger.info("Created note",
          "noteId", note.id.toString,
          "homeId", note.homeId.map(_.toString).getOrElse("null"),
          "homeItemId", note.homeItemId.map(_.toString).getOrElse("null"),
          "authorId", note.authorId.toString)
        note
      } else {
        throw new RuntimeException("Failed to create note")
      }
    }
  }

  def updateNote(noteId: UUID, title: Option[String], body: Option[String], noteType: Option[String], updatedBy: UUID): Future[Int] = {
    // Build dynamic update query based on provided fields
    val updates = scala.collection.mutable.ListBuffer[String]()
    val params = scala.collection.mutable.ListBuffer[Any]()

    title.foreach { t =>
      updates += "title = ?"
      params += t
    }

    body.foreach { b =>
      updates += "body = ?"
      params += b
    }

    noteType.foreach { nt =>
      updates += "note_type = ?"
      params += nt
    }

    if (updates.isEmpty) {
      return Future.successful(0)
    }

    updates += "updated_by = ?"
    updates += "updated_at = ?"
    params += updatedBy
    params += Timestamp.valueOf(LocalDateTime.now())

    params += noteId

    val sql = s"""
      UPDATE notes
      SET ${updates.mkString(", ")}
      WHERE id = ? AND deleted_at IS NULL
    """

    DatabaseConnection.executeUpdate(sql, params.toList*).map { rowsAffected =>
      if (rowsAffected > 0) {
        logger.info("Updated note",
          "noteId", noteId.toString,
          "updatedBy", updatedBy.toString,
          "fieldsUpdated", updates.size)
      }
      rowsAffected
    }
  }

  def pinNote(noteId: UUID, updatedBy: UUID): Future[Int] = {
    val sql = """
      UPDATE notes
      SET is_pinned = true, updated_by = ?, updated_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """

    val now = Timestamp.valueOf(LocalDateTime.now())

    DatabaseConnection.executeUpdate(sql, updatedBy, now, noteId).map { rowsAffected =>
      logger.info("Pinned note",
        "noteId", noteId.toString,
        "updatedBy", updatedBy.toString,
        "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def unpinNote(noteId: UUID, updatedBy: UUID): Future[Int] = {
    val sql = """
      UPDATE notes
      SET is_pinned = false, updated_by = ?, updated_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """

    val now = Timestamp.valueOf(LocalDateTime.now())

    DatabaseConnection.executeUpdate(sql, updatedBy, now, noteId).map { rowsAffected =>
      logger.info("Unpinned note",
        "noteId", noteId.toString,
        "updatedBy", updatedBy.toString,
        "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def softDeleteNote(noteId: UUID, deletedBy: UUID): Future[Int] = {
    val sql = """
      UPDATE notes
      SET deleted_at = ?, updated_by = ?, updated_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """

    val now = Timestamp.valueOf(LocalDateTime.now())

    DatabaseConnection.executeUpdate(sql, now, deletedBy, now, noteId).map { rowsAffected =>
      if (rowsAffected > 0) {
        logger.info("Soft deleted note",
          "noteId", noteId.toString,
          "deletedBy", deletedBy.toString)
      }
      rowsAffected
    }
  }

  def findNotesByHomeId(homeId: UUID): Future[List[Note]] = {
    val sql = """
      SELECT
        id, home_id, home_item_id, title, body, note_type,
        is_pinned, author_id, created_by, created_at, updated_by, updated_at, deleted_at
      FROM notes
      WHERE home_id = ? AND deleted_at IS NULL
      ORDER BY created_at DESC
    """

    DatabaseConnection.executeQuery(sql, homeId)(Note.fromResultSet).map { notes =>
      logger.info("Found notes by home ID",
        "homeId", homeId.toString,
        "count", notes.length)
      notes
    }
  }

  def findNotesByHomeItemId(homeItemId: UUID): Future[List[Note]] = {
    val sql = """
      SELECT
        id, home_id, home_item_id, title, body, note_type,
        is_pinned, author_id, created_by, created_at, updated_by, updated_at, deleted_at
      FROM notes
      WHERE home_item_id = ? AND deleted_at IS NULL
      ORDER BY created_at DESC
    """

    DatabaseConnection.executeQuery(sql, homeItemId)(Note.fromResultSet).map { notes =>
      logger.info("Found notes by home item ID",
        "homeItemId", homeItemId.toString,
        "count", notes.length)
      notes
    }
  }

  def findNoteById(noteId: UUID): Future[Option[Note]] = {
    val sql = """
      SELECT
        id, home_id, home_item_id, title, body, note_type,
        is_pinned, author_id, created_by, created_at, updated_by, updated_at, deleted_at
      FROM notes
      WHERE id = ? AND deleted_at IS NULL
    """

    DatabaseConnection.executeQuerySingle(sql, noteId)(Note.fromResultSet)
  }
}
