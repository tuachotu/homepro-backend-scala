package com.tuachotu.model.response

import spray.json._
case class UserLoginResponse(id: String, name: String, roleType: String)

object UserLoginResponseProtocol extends DefaultJsonProtocol {
  implicit val UserLoginResponseFormat: RootJsonFormat[UserLoginResponse] = jsonFormat3(UserLoginResponse.apply)
}
