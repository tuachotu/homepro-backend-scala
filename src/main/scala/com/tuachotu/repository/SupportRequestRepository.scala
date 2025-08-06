package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.SupportRequest
import com.tuachotu.model.request.CreateSupportRequest
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.getLogger

import java.time.LocalDateTime
import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class SupportRequestRepository(implicit ec: ExecutionContext) {
  implicit val clazz: Class[?] = classOf[SupportRequestRepository]

  def findAll(): Future[List[SupportRequest]] = {
    val sql = """
      SELECT id, homeowner_id, home_id, title, description, status, priority, 
             assigned_expert_id, created_at, created_by, updated_at, updated_by
      FROM support_requests
      ORDER BY created_at DESC
    """
    
    DatabaseConnection.executeQuery(sql)(SupportRequest.fromResultSet)
  }

  def findById(id: UUID): Future[Option[SupportRequest]] = {
    val sql = """
      SELECT id, homeowner_id, home_id, title, description, status, priority, 
             assigned_expert_id, created_at, created_by, updated_at, updated_by
      FROM support_requests 
      WHERE id = ?
    """
    
    DatabaseConnection.executeQuerySingle(sql, id)(SupportRequest.fromResultSet)
  }

  def findByHomeownerId(homeownerId: UUID): Future[List[SupportRequest]] = {
    val sql = """
      SELECT id, homeowner_id, home_id, title, description, status, priority, 
             assigned_expert_id, created_at, created_by, updated_at, updated_by
      FROM support_requests 
      WHERE homeowner_id = ?
      ORDER BY created_at DESC
    """
    
    DatabaseConnection.executeQuery(sql, homeownerId)(SupportRequest.fromResultSet)
  }

  def findByStatus(status: String): Future[List[SupportRequest]] = {
    val sql = """
      SELECT id, homeowner_id, home_id, title, description, status, priority, 
             assigned_expert_id, created_at, created_by, updated_at, updated_by
      FROM support_requests 
      WHERE status = ?
      ORDER BY created_at DESC
    """
    
    DatabaseConnection.executeQuery(sql, status)(SupportRequest.fromResultSet)
  }

  def findByAssignedExpert(expertId: UUID): Future[List[SupportRequest]] = {
    val sql = """
      SELECT id, homeowner_id, home_id, title, description, status, priority, 
             assigned_expert_id, created_at, created_by, updated_at, updated_by
      FROM support_requests 
      WHERE assigned_expert_id = ?
      ORDER BY created_at DESC
    """
    
    DatabaseConnection.executeQuery(sql, expertId)(SupportRequest.fromResultSet)
  }

  def create(csr: CreateSupportRequest): Future[SupportRequest] = {
    val now = LocalDateTime.now()
    val supportRequestId = UUID.randomUUID()

    val sql = """
      INSERT INTO support_requests (id, homeowner_id, home_id, title, description, status, priority, 
                                   assigned_expert_id, created_at, created_by, updated_at, updated_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    val supportRequest = SupportRequest(
      id = supportRequestId,
      homeownerId = csr.homeownerId,
      homeId = csr.homeId,
      title = csr.title,
      description = csr.description,
      status = "open",
      priority = csr.priority,
      assignedExpertId = csr.assignedExpertId,
      createdAt = now,
      createdBy = csr.homeownerId,
      updatedAt = now,
      updatedBy = None
    )

    DatabaseConnection.executeUpdate(
      sql,
      supportRequest.id,
      supportRequest.homeownerId,
      supportRequest.homeId.orNull,
      supportRequest.title,
      supportRequest.description.orNull,
      supportRequest.status,
      supportRequest.priority,
      supportRequest.assignedExpertId.orNull,
      Timestamp.valueOf(supportRequest.createdAt),
      supportRequest.createdBy,
      Timestamp.valueOf(supportRequest.updatedAt),
      supportRequest.updatedBy.orNull
    ).map { rowsAffected =>
      LoggerUtil.info("Support request created", 
        "supportRequestId", supportRequest.id, 
        "homeownerId", supportRequest.homeownerId, 
        "rowsAffected", rowsAffected)
      supportRequest
    }
  }

  def updateStatus(id: UUID, status: String, updatedBy: UUID): Future[Int] = {
    val sql = """
      UPDATE support_requests 
      SET status = ?, updated_at = ?, updated_by = ?
      WHERE id = ?
    """
    
    val now = LocalDateTime.now()
    
    DatabaseConnection.executeUpdate(
      sql,
      status,
      Timestamp.valueOf(now),
      updatedBy,
      id
    ).map { rowsAffected =>
      LoggerUtil.info("Support request status updated", 
        "supportRequestId", id, 
        "status", status, 
        "updatedBy", updatedBy, 
        "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def assignExpert(id: UUID, expertId: UUID, updatedBy: UUID): Future[Int] = {
    val sql = """
      UPDATE support_requests 
      SET assigned_expert_id = ?, updated_at = ?, updated_by = ?
      WHERE id = ?
    """
    
    val now = LocalDateTime.now()
    
    DatabaseConnection.executeUpdate(
      sql,
      expertId,
      Timestamp.valueOf(now),
      updatedBy,
      id
    ).map { rowsAffected =>
      LoggerUtil.info("Expert assigned to support request", 
        "supportRequestId", id, 
        "expertId", expertId, 
        "updatedBy", updatedBy, 
        "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def update(supportRequest: SupportRequest): Future[Int] = {
    val sql = """
      UPDATE support_requests 
      SET homeowner_id = ?, home_id = ?, title = ?, description = ?, status = ?, 
          priority = ?, assigned_expert_id = ?, updated_at = ?, updated_by = ?
      WHERE id = ?
    """
    
    DatabaseConnection.executeUpdate(
      sql,
      supportRequest.homeownerId,
      supportRequest.homeId.orNull,
      supportRequest.title,
      supportRequest.description.orNull,
      supportRequest.status,
      supportRequest.priority,
      supportRequest.assignedExpertId.orNull,
      Timestamp.valueOf(supportRequest.updatedAt),
      supportRequest.updatedBy.orNull,
      supportRequest.id
    ).map { rowsAffected =>
      LoggerUtil.info("Support request updated", 
        "supportRequestId", supportRequest.id, 
        "rowsAffected", rowsAffected)
      rowsAffected
    }
  }

  def delete(id: UUID): Future[Int] = {
    val sql = "DELETE FROM support_requests WHERE id = ?"
    
    DatabaseConnection.executeUpdate(sql, id).map { rowsAffected =>
      LoggerUtil.info("Support request deleted", "supportRequestId", id, "rowsAffected", rowsAffected)
      rowsAffected
    }
  }
}
