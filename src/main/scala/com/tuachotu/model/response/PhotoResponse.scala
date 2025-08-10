package com.tuachotu.model.response

import java.time.LocalDateTime
import java.util.UUID
import spray.json._

case class HomeItemInfo(
  id: String,
  name: String,
  `type`: String
)

case class UploadedByInfo(
  id: String,
  name: String,
  email: String
)

case class PhotoResponse(
  id: String,
  file_name: String,
  caption: Option[String],
  is_primary: Boolean,
  created_at: String,
  url: String,
  home_item: Option[HomeItemInfo],
  uploaded_by: Option[UploadedByInfo]
)

object PhotoResponseProtocol extends DefaultJsonProtocol {
  implicit val homeItemInfoFormat: RootJsonFormat[HomeItemInfo] = jsonFormat3(HomeItemInfo.apply)
  implicit val uploadedByInfoFormat: RootJsonFormat[UploadedByInfo] = jsonFormat3(UploadedByInfo.apply)
  implicit val photoResponseFormat: RootJsonFormat[PhotoResponse] = jsonFormat8(PhotoResponse.apply)
}