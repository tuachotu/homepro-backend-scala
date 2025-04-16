package com.tuachotu.model.db
import java.util.UUID
import java.time.LocalDateTime
import slick.jdbc.PostgresProfile.api._

case class SupportRequest(
                           id: UUID,
                           homeownerId: UUID,
                           homeId: Option[UUID],
                           title: String,
                           description: Option[String],
                           status: String,
                           priority: String,
                           assignedExpertId: Option[UUID],
                           createdAt: LocalDateTime,
                           createdBy: UUID,
                           updatedAt: LocalDateTime,
                           updatedBy: Option[UUID]
                         )

object SupportRequest {
    val tupled = apply.tupled
}

class SupportRequests(tag: Tag) extends Table[SupportRequest](tag, "support_requests"){

    val profile = slick.jdbc.PostgresProfile
    import profile.api._

    def id = column[UUID]("id", O.PrimaryKey)
    def homeownerId = column[UUID]("homeowner_id")
    def homeId = column[Option[UUID]]("home_id")
    def title = column[String]("title")
    def description = column[Option[String]]("description")
    def status = column[String]("status")
    def priority = column[String]("priority")
    def assignedExpertId = column[Option[UUID]]("assigned_expert_id")
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
      createdAt,
      createdBy,
      updatedAt,
      updatedBy
    ) <> (SupportRequest.tupled, SupportRequest.unapply)
}