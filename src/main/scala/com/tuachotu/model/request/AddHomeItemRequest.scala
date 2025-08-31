package com.tuachotu.model.request

import spray.json._
import java.util.UUID
import com.tuachotu.model.db.HomeItemType
import com.tuachotu.util.JsonFormats._

case class AddHomeItemRequest(
  name: String,
  itemType: String,
  isEmergency: Boolean = false,
  data: Option[String] = None
)

object AddHomeItemRequestJsonProtocol extends DefaultJsonProtocol {
  implicit val addHomeItemRequestFormat: RootJsonFormat[AddHomeItemRequest] =
    jsonFormat4(AddHomeItemRequest.apply)
}