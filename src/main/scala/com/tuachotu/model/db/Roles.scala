package com.tuachotu.model.db

import java.sql.{ResultSet, Timestamp}

case class Role(
  id: Int,
  name: String,
  description: Option[String],
  createdAt: Timestamp,
  lastModifiedAt: Timestamp,
  deletedAt: Option[Timestamp]
)

object Role {
  def fromResultSet(rs: ResultSet): Role = {
    Role(
      id = rs.getInt("id"),
      name = rs.getString("name"),
      description = Option(rs.getString("description")),
      createdAt = rs.getTimestamp("created_at"),
      lastModifiedAt = rs.getTimestamp("updated_at"),
      deletedAt = Option(rs.getTimestamp("deleted_at"))
    )
  }
}