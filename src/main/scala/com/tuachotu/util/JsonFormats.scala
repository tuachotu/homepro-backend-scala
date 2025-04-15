package com.tuachotu.util

import spray.json._
import java.util.UUID

object JsonFormats extends DefaultJsonProtocol {

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(json: JsValue): UUID = json match {
      case JsString(s) => UUID.fromString(s)
      case _           => deserializationError("Expected UUID as JsString")
    }
  }

}