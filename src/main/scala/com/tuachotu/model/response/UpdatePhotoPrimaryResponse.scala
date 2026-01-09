package com.tuachotu.model.response

import spray.json._

case class UpdatePhotoPrimaryResponse(
  id: String,
  homeItemId: Option[String],
  fileName: String,
  isPrimary: Boolean,
  message: String
)

object UpdatePhotoPrimaryResponseProtocol extends DefaultJsonProtocol {
  implicit val updatePhotoPrimaryResponseFormat: RootJsonFormat[UpdatePhotoPrimaryResponse] =
    jsonFormat5(UpdatePhotoPrimaryResponse.apply)
}
