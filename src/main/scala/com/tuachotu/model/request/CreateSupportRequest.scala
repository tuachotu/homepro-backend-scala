package com.tuachotu.model.request

import spray.json._
import java.util.UUID
import com.tuachotu.model.db._
import com.tuachotu.util.SupportStatus
import com.tuachotu.util.JsonFormats._// Brings in UUIDFormat and SupportStatusJsonFormat
import com.tuachotu.util.IdUtil

case class CreateSupportRequest(
                                 homeownerId: UUID,
                                 homeId: Option[UUID],
                                 title: String,
                                 description: Option[String] = None,
                                 assignedExpertId: Option[UUID] = None,
                                 status: SupportStatus.Value = SupportStatus.Open,
                                 priority: String = "medium"
                               )

object CreateSupportRequest {
  def createSupportRequestWithoutHomeMapping(userID: UUID): CreateSupportRequest =
    CreateSupportRequest(userID, None, IdUtil.defaultSupportReqTitle(userID))
}

object CreateSupportRequestJsonProtocol extends DefaultJsonProtocol {
  // Case class formatter
  implicit val createSupportRequestFormat: RootJsonFormat[CreateSupportRequest] =
    jsonFormat7(CreateSupportRequest.apply)
}