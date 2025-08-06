package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{UserRole, Role, User}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.getLogger

import java.util.UUID
import java.sql.{ResultSet, Timestamp}
import scala.concurrent.{ExecutionContext, Future}

class UserRoleRepository(implicit ec: ExecutionContext) {
  implicit val clazz: Class[?] = classOf[UserRoleRepository]

  def findRoleIdsByUserId(userId: UUID): Future[List[Int]] = {
    val sql = """
      SELECT role_id
      FROM user_roles 
      WHERE user_id = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuery(sql, userId) { rs =>
      rs.getInt("role_id")
    }
  }

  def findUserRolesByUserId(userId: UUID): Future[List[UserRole]] = {
    val sql = """
      SELECT id, user_id, role_id, created_at, deleted_at
      FROM user_roles 
      WHERE user_id = ? AND deleted_at IS NULL
      ORDER BY created_at ASC
    """
    
    DatabaseConnection.executeQuery(sql, userId)(UserRole.fromResultSet)
  }

  def findUsersByRoleId(roleId: Int): Future[List[UUID]] = {
    val sql = """
      SELECT user_id
      FROM user_roles 
      WHERE role_id = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuery(sql, roleId) { rs =>
      rs.getObject("user_id", classOf[UUID])
    }
  }

  def findUsersWithRoles(): Future[List[(User, List[Role])]] = {
    val sql = """
      SELECT u.id, u.firebase_uid, u.name, u.email, u.phone_number, u.profile, 
             u.created_by, u.last_modified_by, u.created_at, u.last_modified_at, u.deleted_at,
             r.id as role_id, r.name as role_name, r.description as role_description, 
             r.created_at as role_created_at, r.updated_at as role_updated_at, 
             r.deleted_at as role_deleted_at
      FROM users u
      LEFT JOIN user_roles ur ON u.id = ur.user_id AND ur.deleted_at IS NULL
      LEFT JOIN roles r ON ur.role_id = r.id AND r.deleted_at IS NULL
      WHERE u.deleted_at IS NULL
      ORDER BY u.created_at DESC, r.name ASC
    """
    
    DatabaseConnection.executeQuery(sql) { rs =>
      val user = User.fromResultSet(rs)
      val role = if (rs.getString("role_id") != null) {
        Some(Role(
          id = rs.getInt("role_id"),
          name = rs.getString("role_name"),
          description = Option(rs.getString("role_description")),
          createdAt = rs.getTimestamp("role_created_at"),
          lastModifiedAt = rs.getTimestamp("role_updated_at"),
          deletedAt = Option(rs.getTimestamp("role_deleted_at"))
        ))
      } else None
      (user, role)
    }.map { results =>
      results.groupBy(_._1).map { case (user, userRolePairs) =>
        val roles = userRolePairs.flatMap(_._2).toList
        (user, roles)
      }.toList
    }
  }

  def assignRoleToUser(userId: UUID, roleId: Int): Future[Int] = {
    // First check if the assignment already exists
    val checkSql = """
      SELECT COUNT(*) as count
      FROM user_roles 
      WHERE user_id = ? AND role_id = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(checkSql, userId, roleId) { rs =>
      rs.getInt("count")
    }.flatMap { countOpt =>
      val count = countOpt.getOrElse(0)
      if (count > 0) {
        LoggerUtil.warn("User role assignment already exists", "userId", userId, "roleId", roleId)
        Future.successful(0) // Already exists
      } else {
        val insertSql = """
          INSERT INTO user_roles (user_id, role_id, created_at)
          VALUES (?, ?, ?)
        """
        
        val now = new Timestamp(System.currentTimeMillis())
        
        DatabaseConnection.executeUpdate(insertSql, userId, roleId, now).map { rowsAffected =>
          LoggerUtil.info("Role assigned to user", "userId", userId, "roleId", roleId, "rowsAffected", rowsAffected)
          rowsAffected
        }
      }
    }
  }

  def removeRoleFromUser(userId: UUID, roleId: Int): Future[Int] = {
    val sql = """
      UPDATE user_roles 
      SET deleted_at = ?
      WHERE user_id = ? AND role_id = ? AND deleted_at IS NULL
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(sql, now, userId, roleId).map { rowsAffected =>
      LoggerUtil.info("Role removed from user", "userId", userId, "roleId", roleId, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def hardDeleteUserRole(userId: UUID, roleId: Int): Future[Int] = {
    val sql = "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?"
    
    DatabaseConnection.executeUpdate(sql, userId, roleId).map { rowsAffected =>
      LoggerUtil.info("User role hard deleted", "userId", userId, "roleId", roleId, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def findById(id: Int): Future[Option[UserRole]] = {
    val sql = """
      SELECT id, user_id, role_id, created_at, deleted_at
      FROM user_roles 
      WHERE id = ?
    """
    
    DatabaseConnection.executeQuerySingle(sql, id)(UserRole.fromResultSet)
  }
}