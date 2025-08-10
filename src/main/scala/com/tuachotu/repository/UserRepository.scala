package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.User
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.getLogger

import java.util.UUID
import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

class UserRepository(implicit ec: ExecutionContext) {
  implicit val clazz: Class[?] = classOf[UserRepository]

  def findAll(): Future[List[User]] = {
    val sql = """
      SELECT id, firebase_uid, name, email, phone_number, profile, 
             created_by, last_modified_by, created_at, last_modified_at, deleted_at
      FROM users 
      WHERE deleted_at IS NULL
      ORDER BY created_at DESC
    """
    
    DatabaseConnection.executeQuery(sql)(User.fromResultSet)
  }

  def findById(id: UUID): Future[Option[User]] = {
    val sql = """
      SELECT id, firebase_uid, name, email, phone_number, profile, 
             created_by, last_modified_by, created_at, last_modified_at, deleted_at
      FROM users 
      WHERE id = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(sql, id)(User.fromResultSet)
  }

  def findByFirebaseId(firebaseUid: String): Future[Option[User]] = {
    val sql = """
      SELECT id, firebase_uid, name, email, phone_number, profile, 
             created_by, last_modified_by, created_at, last_modified_at, deleted_at
      FROM users 
      WHERE firebase_uid = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(sql, firebaseUid)(User.fromResultSet)
  }

  def findByEmail(email: String): Future[Option[User]] = {
    val sql = """
      SELECT id, firebase_uid, name, email, phone_number, profile, 
             created_by, last_modified_by, created_at, last_modified_at, deleted_at
      FROM users 
      WHERE email = ? AND deleted_at IS NULL
    """
    
    DatabaseConnection.executeQuerySingle(sql, email)(User.fromResultSet)
  }

  def create(user: User): Future[Int] = {
    val sql = """
      INSERT INTO users (id, firebase_uid, name, email, phone_number, profile, 
                        created_by, last_modified_by, created_at, last_modified_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(
      sql,
      user.id,
      user.firebaseUid,
      user.name.orNull,
      user.email.orNull,
      user.phoneNumber.orNull,
      user.profile.orNull,
      user.createdBy.orNull,
      user.lastModifiedBy.orNull,
      now,
      now
    ).map { rowsAffected =>
      LoggerUtil.info("User created", "userId", user.id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def update(user: User): Future[Int] = {
    val sql = """
      UPDATE users 
      SET firebase_uid = ?, name = ?, email = ?, phone_number = ?, profile = ?, 
          last_modified_by = ?, last_modified_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(
      sql,
      user.firebaseUid,
      user.name.orNull,
      user.email.orNull,
      user.phoneNumber.orNull,
      user.profile.orNull,
      user.lastModifiedBy.orNull,
      now,
      user.id
    ).map { rowsAffected =>
      LoggerUtil.info("User updated", "userId", user.id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def softDelete(id: UUID, deletedBy: String): Future[Int] = {
    val sql = """
      UPDATE users 
      SET deleted_at = ?, last_modified_by = ?, last_modified_at = ?
      WHERE id = ? AND deleted_at IS NULL
    """
    
    val now = new Timestamp(System.currentTimeMillis())
    
    DatabaseConnection.executeUpdate(sql, now, deletedBy, now, id).map { rowsAffected =>
      LoggerUtil.info("User soft deleted", "userId", id, "deletedBy", deletedBy, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def hardDelete(id: UUID): Future[Int] = {
    val sql = "DELETE FROM users WHERE id = ?"
    
    DatabaseConnection.executeUpdate(sql, id).map { rowsAffected =>
      LoggerUtil.info("User hard deleted", "userId", id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }
}