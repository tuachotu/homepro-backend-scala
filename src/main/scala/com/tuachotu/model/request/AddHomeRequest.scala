package com.tuachotu.model.request

import spray.json._

case class AddHomeRequest(
  name: String,
  address: Option[String] = None,
  metadata: Option[Map[String, String]] = None
)

object AddHomeRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val addHomeRequestFormat: RootJsonFormat[AddHomeRequest] = jsonFormat3(AddHomeRequest.apply)
}