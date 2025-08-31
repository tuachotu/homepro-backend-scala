package com.tuachotu.model.response

import spray.json._

case class PhotoUploadResponse(
  id: String,
  homeItemId: String,
  fileName: String,
  s3Key: String,
  contentType: Option[String],
  caption: Option[String],
  message: String,
  photoUrl: Option[String] = None
)

object PhotoUploadResponseProtocol extends DefaultJsonProtocol {
  implicit val photoUploadResponseFormat: RootJsonFormat[PhotoUploadResponse] =
    jsonFormat8(PhotoUploadResponse.apply)
}