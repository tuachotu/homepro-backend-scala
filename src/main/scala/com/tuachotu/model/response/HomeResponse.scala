package com.tuachotu.model.response

import spray.json._

case class HomeStatsResponse(
  total_items: Int,
  total_photos: Int,
  emergency_items: Int
)

case class HomeResponse(
  id: String,
  name: Option[String],
  address: Option[String],
  role: String,
  created_at: String,
  updated_at: String,
  stats: HomeStatsResponse
)

case class HomeItemResponse(
  id: String,
  name: String,
  `type`: String,
  is_emergency: Boolean,
  data: Map[String, Any],
  created_at: String,
  photo_count: Int,
  primary_photo_url: Option[String]
)

object HomeResponseProtocol extends DefaultJsonProtocol {
  implicit val homeStatsResponseFormat: RootJsonFormat[HomeStatsResponse] = jsonFormat3(HomeStatsResponse.apply)
  implicit val homeResponseFormat: RootJsonFormat[HomeResponse] = jsonFormat7(HomeResponse.apply)
  
  // Custom JSON format for Map[String, Any] to handle JSONB data
  implicit val anyFormat: JsonFormat[Any] = new JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case n: Int => JsNumber(n)
      case n: Long => JsNumber(n)
      case n: Double => JsNumber(n)
      case n: BigDecimal => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean => JsBoolean(b)
      case null => JsNull
      case seq: Seq[_] => JsArray(seq.map(write).toVector)
      case map: Map[String, _] => JsObject(map.map { case (k, v) => k -> write(v) })
      case _ => JsString(x.toString)
    }
    
    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
      case JsNull => null
      case JsArray(elements) => elements.map(read)
      case JsObject(fields) => fields.map { case (k, v) => k -> read(v) }
    }
  }
  
  implicit val mapStringAnyFormat: RootJsonFormat[Map[String, Any]] = mapFormat[String, Any]
  implicit val homeItemResponseFormat: RootJsonFormat[HomeItemResponse] = jsonFormat8(HomeItemResponse.apply)
}