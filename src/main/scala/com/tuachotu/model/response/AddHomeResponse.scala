package com.tuachotu.model.response

import spray.json._
import com.tuachotu.model.db.Home
import com.tuachotu.util.TimeUtil

case class AddHomeResponse(
  id: String,
  name: String,
  address: Option[String],
  isPrimary: Boolean,
  createdAt: String,
  message: String
)

object AddHomeResponse {
  def fromDbModel(home: Home): AddHomeResponse = {
    AddHomeResponse(
      id = home.id.toString,
      name = home.name.getOrElse(""),
      address = home.address,
      isPrimary = home.isPrimary,
      createdAt = TimeUtil.formatLocalDateTime(home.createdAt),
      message = "Home created successfully"
    )
  }
}

object AddHomeResponseProtocol extends DefaultJsonProtocol {
  implicit val addHomeResponseFormat: RootJsonFormat[AddHomeResponse] = jsonFormat6(AddHomeResponse.apply)
}