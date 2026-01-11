package com.tuachotu.model.response

import spray.json._
import com.tuachotu.model.db.{Note, NoteType}

case class NoteResponse(
  id: String,
  homeId: Option[String],
  homeItemId: Option[String],
  title: Option[String],
  body: String,
  noteType: String,
  isPinned: Boolean,
  authorId: String,
  createdBy: String,
  createdAt: String,
  updatedBy: Option[String],
  updatedAt: String
)

object NoteResponse {
  def fromNote(note: Note): NoteResponse =
    NoteResponse(
      id = note.id.toString,
      homeId = note.homeId.map(_.toString),
      homeItemId = note.homeItemId.map(_.toString),
      title = note.title,
      body = note.body,
      noteType = NoteType.toString(note.noteType),
      isPinned = note.isPinned,
      authorId = note.authorId.toString,
      createdBy = note.createdBy.toString,
      createdAt = note.createdAt.toString,
      updatedBy = note.updatedBy.map(_.toString),
      updatedAt = note.updatedAt.toString
    )
}

object NoteResponseProtocol extends DefaultJsonProtocol {
  implicit val noteResponseFormat: RootJsonFormat[NoteResponse] =
    jsonFormat12(NoteResponse.apply)
}
