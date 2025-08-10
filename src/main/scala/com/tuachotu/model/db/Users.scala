package com.tuachotu.model.db

import java.util.UUID
import java.sql.{ResultSet, Timestamp}
import java.time.LocalDateTime

case class User(
  id: UUID,
  firebaseUid: String,
  name: Option[String],
  email: Option[String],
  phoneNumber: Option[String],
  profile: Option[String], // JSON Blob
  createdBy: Option[String],
  lastModifiedBy: Option[String],
  createdAt: LocalDateTime,
  lastModifiedAt: LocalDateTime,
  deletedAt: Option[LocalDateTime]
)

object User {
  def fromResultSet(rs: ResultSet): User = {
    User(
      id = rs.getObject("id", classOf[UUID]),
      firebaseUid = rs.getString("firebase_uid"),
      name = Option(rs.getString("name")),
      email = Option(rs.getString("email")),
      phoneNumber = Option(rs.getString("phone_number")),
      profile = Option(rs.getString("profile")),
      createdBy = Option(rs.getString("created_by")),
      lastModifiedBy = Option(rs.getString("last_modified_by")),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      lastModifiedAt = rs.getTimestamp("last_modified_at").toLocalDateTime,
      deletedAt = Option(rs.getTimestamp("deleted_at")).map(_.toLocalDateTime)
    )
  }
}