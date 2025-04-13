package com.tuachotu.model.db

import slick.jdbc.PostgresProfile.api.*
import java.util.UUID
import java.time.LocalDateTime

case class SupportRequest(
                           id: UUID,
                           homeownerId: UUID,
                           homeId: Option[UUID],
                           title: String,
                           description: Option[String],
                           status: String,
                           priority: String,
                           assignedExpertId: Option[UUID],
                           info: String, // Consider mapping to JSON later
                           createdAt: LocalDateTime,
                           createdBy: UUID,
                           updatedAt: LocalDateTime,
                           updatedBy: Option[UUID]
                         )

object SupportRequest {
    val tupled = apply.tupled
}

class SupportRequests(tag: Tag) extends Table[SupportRequest](tag, "support_requests") {
    def id = column[UUID]("id", O.PrimaryKey)
    def homeownerId = column[UUID]("homeowner_id")
    def homeId = column[Option[UUID]]("home_id")
    def title = column[String]("title")
    def description = column[Option[String]]("description")
    def status = column[String]("status")
    def priority = column[String]("priority")
    def assignedExpertId = column[Option[UUID]]("assigned_expert_id")
    def info = column[String]("info")
    def createdAt = column[LocalDateTime]("created_at")
    def createdBy = column[UUID]("created_by")
    def updatedAt = column[LocalDateTime]("updated_at")
    def updatedBy = column[Option[UUID]]("updated_by")

    def * = (
      id,
      homeownerId,
      homeId,
      title,
      description,
      status,
      priority,
      assignedExpertId,
      info,
      createdAt,
      createdBy,
      updatedAt,
      updatedBy
    ) <> (SupportRequest.tupled, SupportRequest.unapply)
}