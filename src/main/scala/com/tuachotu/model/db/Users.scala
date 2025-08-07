package com.tuachotu.model.db

import java.util.UUID
import java.sql.{ResultSet, Timestamp}

case class User(
                 id: UUID,
                 firebaseUid: Option[String],
                 name: Option[String],
                 email: Option[String],
                 phoneNumber: Option[String],
                 profile: Option[String], // JSON Blob
                 createdBy: Option[String],
                 lastModifiedBy: Option[String],
                 createdAt: Timestamp,
                 lastModifiedAt: Timestamp,
                 deletedAt: Option[Timestamp]
               )

object User {
  def fromResultSet(rs: ResultSet): User = {
    User(
      id = rs.getObject("id", classOf[UUID]),
      firebaseUid = Option(rs.getString("firebase_uid")),
      name = Option(rs.getString("name")),
      email = Option(rs.getString("email")),
      phoneNumber = Option(rs.getString("phone_number")),
      profile = Option(rs.getString("profile")),
      createdBy = Option(rs.getString("created_by")),
      lastModifiedBy = Option(rs.getString("last_modified_by")),
      createdAt = rs.getTimestamp("created_at"),
      lastModifiedAt = rs.getTimestamp("last_modified_at"),
      deletedAt = Option(rs.getTimestamp("deleted_at"))
    )
  }
}