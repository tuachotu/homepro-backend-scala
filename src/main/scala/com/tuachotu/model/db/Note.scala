package com.tuachotu.model.db

import java.sql.{ResultSet, Timestamp}
import java.time.LocalDateTime
import java.util.UUID

enum NoteType:
  case General, Observation, Todo, Question

object NoteType:
  def fromString(value: String): NoteType = value.toLowerCase match
    case "general" => General
    case "observation" => Observation
    case "todo" => Todo
    case "question" => Question
    case _ => throw new IllegalArgumentException(s"Invalid note type: $value")

  def toString(noteType: NoteType): String = noteType match
    case General => "general"
    case Observation => "observation"
    case Todo => "todo"
    case Question => "question"

case class Note(
  id: UUID,
  homeId: Option[UUID],
  homeItemId: Option[UUID],
  title: Option[String],
  body: String,
  noteType: NoteType,
  isPinned: Boolean,
  authorId: UUID,
  createdBy: UUID,
  createdAt: LocalDateTime,
  updatedBy: Option[UUID],
  updatedAt: LocalDateTime,
  deletedAt: Option[LocalDateTime]
)

object Note {
  def fromResultSet(rs: ResultSet): Note = {
    Note(
      id = rs.getObject("id").asInstanceOf[UUID],
      homeId = Option(rs.getObject("home_id")).map(_.asInstanceOf[UUID]),
      homeItemId = Option(rs.getObject("home_item_id")).map(_.asInstanceOf[UUID]),
      title = Option(rs.getString("title")),
      body = rs.getString("body"),
      noteType = NoteType.fromString(rs.getString("note_type")),
      isPinned = rs.getBoolean("is_pinned"),
      authorId = rs.getObject("author_id").asInstanceOf[UUID],
      createdBy = rs.getObject("created_by").asInstanceOf[UUID],
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedBy = Option(rs.getObject("updated_by")).map(_.asInstanceOf[UUID]),
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime,
      deletedAt = Option(rs.getTimestamp("deleted_at")).map(_.toLocalDateTime)
    )
  }
}
