package com.tuachotu.util

import spray.json._

object SupportStatus extends Enumeration {
  type SupportStatus = Value
  val Open = Value("open")
  val InProgress = Value("in_progress")
  val Resolved = Value("resolved")
  val Closed = Value("closed")

  implicit object SupportStatusJsonFormat extends JsonFormat[SupportStatus.Value] {
    def write(obj: SupportStatus.Value): JsValue = JsString(obj.toString)

    def read(json: JsValue): SupportStatus.Value = json match {
      case JsString(str) => SupportStatus.withName(str)
      case _             => deserializationError("SupportStatus expected")
    }
  }
}