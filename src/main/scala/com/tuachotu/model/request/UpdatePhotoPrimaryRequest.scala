package com.tuachotu.model.request

import spray.json._

case class UpdatePhotoPrimaryRequest(
  isPrimary: Boolean
)

object UpdatePhotoPrimaryRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val updatePhotoPrimaryRequestFormat: RootJsonFormat[UpdatePhotoPrimaryRequest] =
    jsonFormat1(UpdatePhotoPrimaryRequest.apply)
}
