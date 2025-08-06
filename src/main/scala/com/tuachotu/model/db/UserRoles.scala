package com.tuachotu.model.db

import java.util.UUID
import java.sql.{ResultSet, Timestamp}

case class UserRole(
                     id: Int,
                     userId: UUID,
                     roleId: Int,
                     createdAt: Option[Timestamp],
                     deletedAt: Option[Timestamp]
                   )

object UserRole {
  def fromResultSet(rs: ResultSet): UserRole = {
    UserRole(
      id = rs.getInt("id"),
      userId = rs.getObject("user_id", classOf[UUID]),
      roleId = rs.getInt("role_id"),
      createdAt = Option(rs.getTimestamp("created_at")),
      deletedAt = Option(rs.getTimestamp("deleted_at"))
    )
  }
}