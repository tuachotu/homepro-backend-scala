package com.tuachotu.model.response

import spray.json._
import com.tuachotu.model.db.HomeItem

case class AddHomeItemResponse(
  id: String,
  homeId: String,
  name: String,
  `type`: String,
  isEmergency: Boolean,
  createdAt: String,
  message: String
)

object AddHomeItemResponse {
  def fromHomeItem(homeItem: HomeItem, message: String = "Home item created successfully"): AddHomeItemResponse =
    AddHomeItemResponse(
      id = homeItem.id.toString,
      homeId = homeItem.homeId.toString,
      name = homeItem.name,
      `type` = com.tuachotu.model.db.HomeItemType.toString(homeItem.itemType),
      isEmergency = homeItem.isEmergency,
      createdAt = homeItem.createdAt.toString,
      message = message
    )
}

object AddHomeItemResponseProtocol extends DefaultJsonProtocol {
  implicit val addHomeItemResponseFormat: RootJsonFormat[AddHomeItemResponse] =
    jsonFormat7(AddHomeItemResponse.apply)
}