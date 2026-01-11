package com.tuachotu.model.request

import spray.json._

case class UpdateNoteRequest(
  title: Option[String] = None,
  body: Option[String] = None,
  noteType: Option[String] = None
)

object UpdateNoteRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val updateNoteRequestFormat: RootJsonFormat[UpdateNoteRequest] =
    jsonFormat3(UpdateNoteRequest.apply)
}
