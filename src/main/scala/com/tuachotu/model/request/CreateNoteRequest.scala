package com.tuachotu.model.request

import spray.json._

case class CreateNoteRequest(
  homeId: Option[String] = None,
  homeItemId: Option[String] = None,
  title: Option[String] = None,
  body: String,
  noteType: String = "general"
)

object CreateNoteRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val createNoteRequestFormat: RootJsonFormat[CreateNoteRequest] =
    jsonFormat5(CreateNoteRequest.apply)
}
