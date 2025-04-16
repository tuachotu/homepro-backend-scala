package com.tuachotu.model.response

import spray.json._
import java.util.UUID
import java.time.LocalDateTime

import com.tuachotu.model.db.SupportRequest

case class CreateSupportRequestResponse(
                                         id: UUID,
                                         homeownerId: UUID,
                                         homeId: Option[UUID],
                                         title: String,
                                         description: Option[String],
                                         status: String,
                                         priority: String,
                                         assignedExpertId: Option[UUID],
                                       )

object CreateSupportRequestResponse {
  def fromDbModel(sr: SupportRequest): CreateSupportRequestResponse = {
    CreateSupportRequestResponse(
      id = sr.id,
      homeownerId = sr.homeownerId,
      homeId = sr.homeId,
      title = sr.title,
      description = sr.description,
      status = sr.status,
      priority = sr.priority,
      assignedExpertId = sr.assignedExpertId,
    )
  }
}
object CreateSupportRequestResponseJsonProtocol extends DefaultJsonProtocol {
  import com.tuachotu.util.JsonFormats._ // assuming you move shared ones here
  implicit val supportRequestResponseFormat: RootJsonFormat[CreateSupportRequestResponse] =
    jsonFormat8(CreateSupportRequestResponse.apply)
}