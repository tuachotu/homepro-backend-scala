package com.tuachotu.model

import slick.jdbc.PostgresProfile.api._
import java.util.UUID
import java.sql.Timestamp

case class Role(
  id: Int,
  name: String,
  description: Option[String],
  createdAt: Timestamp,
  lastModifiedAt: Timestamp,
  deletedAt: Option[Timestamp]
)

object Role {
  // Companion object to enable `tupled`
  val tupled = (apply _).tupled
}

class Roles(tag: Tag) extends Table[Role](tag, "roles") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def name = column[String]("name", O.Length(255))
  def description = column[Option[String]]("description")
  def createdAt = column[Timestamp]("created_at", O.Default(new Timestamp(System.currentTimeMillis())))
  def lastModifiedAt = column[Timestamp]("updated_at", O.Default(new Timestamp(System.currentTimeMillis())))
  def deletedAt = column[Option[Timestamp]]("deleted_at")

  def * = (id, name, description, createdAt, lastModifiedAt, deletedAt) <>
    (Role.tupled, Role.unapply)
}