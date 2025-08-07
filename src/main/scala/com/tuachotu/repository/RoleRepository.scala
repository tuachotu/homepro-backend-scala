package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.Role
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.getLogger

import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

class RoleRepository(implicit ec: ExecutionContext) {
  implicit val clazz: Class[?] = classOf[RoleRepository]

  def findAll(): Future[List[Role]] = {
    val sql = """
      SELECT id, name, description, created_at, updated_at, deleted_at
      FROM roles 
      WHERE deleted_at IS NULL
      ORDER BY name ASC
    """
    
    DatabaseConnection.executeQuery(sql)(Role.fromResultSet)
  }

  def findById(id: Int): Future[Option[Role]] = {
    val sql = """
      SELECT id, name, description, created_at, updated_at, deleted_at
      FROM roles 
      WHERE id = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(sql, id)(Role.fromResultSet)
  }

  def findByName(name: String): Future[Option[Role]] = {
    val sql = """
      SELECT id, name, description, created_at, updated_at, deleted_at
      FROM roles 
      WHERE name = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(sql, name)(Role.fromResultSet)
  }

  def create(role: Role): Future[Int] = {
    val sql = """
      INSERT INTO roles (name, description, created_at, updated_at)
      VALUES (?, ?, ?, ?)
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(
      sql,
      role.name,
      role.description.orNull,
      now,
      now
    ).map { rowsAffected =>
      LoggerUtil.info("Role created", "roleName", role.name, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def update(role: Role): Future[Int] = {
    val sql = """
      UPDATE roles 
      SET name = ?, description = ?, updated_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(
      sql,
      role.name,
      role.description.orNull,
      now,
      role.id
    ).map { rowsAffected =>
      LoggerUtil.info("Role updated", "roleId", role.id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def softDelete(id: Int): Future[Int] = {
    val sql = """
      UPDATE roles 
      SET deleted_at = ?, updated_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(sql, now, now, id).map { rowsAffected =>
      LoggerUtil.info("Role soft deleted", "roleId", id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def hardDelete(id: Int): Future[Int] = {
    val sql = "DELETE FROM roles WHERE id = ?"
    
    DatabaseConnection.executeUpdate(sql, id).map { rowsAffected =>
      LoggerUtil.info("Role hard deleted", "roleId", id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }
}
