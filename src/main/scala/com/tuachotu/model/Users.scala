package com.tuachotu.model

import slick.jdbc.PostgresProfile.api.*

import java.util.UUID
import java.sql.Timestamp

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
  // Companion object to enable `tupled`
  val tupled = apply.tupled
}

class Users(tag: Tag) extends Table[User](tag, "users") {
  def id = column[UUID]("id", O.PrimaryKey, O.Default(java.util.UUID.randomUUID()))
  def firebaseUid = column[Option[String]]("firebase_uid", O.Unique)
  def name = column[Option[String]]("name", O.Length(500))
  def email = column[Option[String]]("email", O.Unique)
  def phoneNumber = column[Option[String]]("phone_number")
  def profile = column[Option[String]]("profile")
  def createdBy = column[Option[String]]("created_by")
  def lastModifiedBy = column[Option[String]]("last_modified_by")
  def createdAt = column[Timestamp]("created_at", O.Default(new Timestamp(System.currentTimeMillis())))
  def lastModifiedAt = column[Timestamp]("last_modified_at", O.Default(new Timestamp(System.currentTimeMillis())))
  def deletedAt = column[Option[Timestamp]]("deleted_at")

  def * = (id, firebaseUid, name, email, phoneNumber, profile, createdBy, lastModifiedBy, createdAt, lastModifiedAt, deletedAt) <>
    (User.tupled, User.unapply)
}