package com.tuachotu.model

import slick.jdbc.PostgresProfile.api._
import java.util.UUID
import java.sql.Timestamp

case class UserRole(
                     id: Int,
                     userId: UUID,
                     roleId: Int,
                     createdAt: Option[Timestamp],
                     deletedAt: Option[Timestamp]
                   )

object UserRole {
  val tupled = (apply _).tupled
}

class UserRoles(tag: Tag) extends Table[UserRole](tag, "user_roles") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def userId = column[UUID]("user_id")
  def roleId = column[Int]("role_id")
  def createdAt = column[Option[Timestamp]]("created_at")
  def deletedAt = column[Option[Timestamp]]("deleted_at")

  // Foreign Keys
  def user = foreignKey("fk_user_roles_user", userId, TableQuery[Users])(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)
  def role = foreignKey("fk_user_roles_role", roleId, TableQuery[Roles])(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

  // Indexes
  def idxUserId = index("idx_user_id", userId)
  def idxRoleId = index("idx_role_id", roleId)  

  def * = (id, userId, roleId, createdAt, deletedAt) <> (UserRole.tupled, UserRole.unapply)
}