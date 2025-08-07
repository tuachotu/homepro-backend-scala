package com.tuachotu.model.db

import java.util.UUID
import java.time.LocalDateTime
import java.sql.{ResultSet, Timestamp}

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
  def fromResultSet(rs: ResultSet): SupportRequest = {
    SupportRequest(
      id = rs.getObject("id", classOf[UUID]),
      homeownerId = rs.getObject("homeowner_id", classOf[UUID]),
      homeId = Option(rs.getObject("home_id", classOf[UUID])),
      title = rs.getString("title"),
      description = Option(rs.getString("description")),
      status = rs.getString("status"),
      priority = rs.getString("priority"),
      assignedExpertId = Option(rs.getObject("assigned_expert_id", classOf[UUID])),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      createdBy = rs.getObject("created_by", classOf[UUID]),
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime,
      updatedBy = Option(rs.getObject("updated_by", classOf[UUID]))
    )
  }
}