package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{Home, HomeOwner, HomeWithOwnership}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

class HomeRepository()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  def findHomesByUserId(userId: UUID): Future[List[HomeWithOwnership]] = {
    val sql = """
      SELECT 
        h.id, h.address, h.name, h.is_primary, h.metadata, h.created_at, h.created_by, h.updated_at, h.updated_by,
        ho.role,
        COALESCE(item_stats.total_items, 0) as total_items,
        COALESCE(photo_stats.total_photos, 0) as total_photos,
        COALESCE(item_stats.emergency_items, 0) as emergency_items
      FROM homes h
      JOIN home_owners ho ON h.id = ho.home_id
      LEFT JOIN (
        SELECT 
          home_id,
          COUNT(*) as total_items,
          COUNT(CASE WHEN is_emergency THEN 1 END) as emergency_items
        FROM home_items
        GROUP BY home_id
      ) item_stats ON h.id = item_stats.home_id
      LEFT JOIN (
        SELECT 
          home_id,
          COUNT(*) as total_photos
        FROM photos
        WHERE home_id IS NOT NULL
        GROUP BY home_id
      ) photo_stats ON h.id = photo_stats.home_id
      WHERE ho.user_id = ?
      ORDER BY h.updated_at DESC
    """

    DatabaseConnection.executeQuery(sql, userId)(extractHomeWithOwnership).map { homes =>
      logger.info(s"Found ${homes.length} homes for user ID: $userId")
      homes
    }
  }

  def findHomeById(homeId: UUID): Future[Option[Home]] = {
    val sql = """
      SELECT id, address, name, is_primary, metadata, created_at, created_by, updated_at, updated_by
      FROM homes
      WHERE id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, homeId)(extractHome)
  }

  def createHome(home: Home): Future[Home] = {
    val sql = """
      INSERT INTO homes (id, address, name, is_primary, metadata, created_at, created_by, updated_at, updated_by)
      VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
    """
    
    val params = List(
      home.id,
      home.address.orNull,
      home.name.orNull,
      home.isPrimary,
      home.metadata,
      Timestamp.valueOf(home.createdAt),
      home.createdBy,
      Timestamp.valueOf(home.updatedAt),
      home.updatedBy.orNull
    )

    DatabaseConnection.executeUpdate(sql, params*).map { rowsAffected =>
      if (rowsAffected == 0) {
        throw new RuntimeException("Failed to create home")
      }
      logger.info(s"Successfully created home ${home.id}")
      home
    }
  }

  def createHomeOwnership(homeId: UUID, userId: UUID, role: String): Future[Unit] = {
    val sql = """
      INSERT INTO home_owners (home_id, user_id, role, added_at)
      VALUES (?, ?, ?, ?)
    """
    
    val params = List(
      homeId,
      userId,
      role,
      Timestamp.valueOf(LocalDateTime.now())
    )

    DatabaseConnection.executeUpdate(sql, params*).map { rowsAffected =>
      if (rowsAffected == 0) {
        throw new RuntimeException("Failed to create home ownership")
      }
      logger.info(s"Successfully created home ownership for user $userId on home $homeId with role $role")
    }
  }

  def countHomesByUserId(userId: UUID): Future[Int] = {
    val sql = """
      SELECT COUNT(*)
      FROM home_owners
      WHERE user_id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, userId)(rs => rs.getInt(1)).map(_.getOrElse(0))
  }

  def checkUserHomeAccess(userId: UUID, homeId: UUID): Future[Option[String]] = {
    val sql = """
      SELECT role
      FROM home_owners
      WHERE user_id = ? AND home_id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, userId, homeId)(rs => rs.getString("role")).map { roleOpt =>
      if (roleOpt.isEmpty) {
        logger.warn(s"User $userId does not have access to home $homeId")
      }
      roleOpt
    }
  }

  private def extractHome(rs: ResultSet): Home = {
    Home(
      id = rs.getObject("id").asInstanceOf[UUID],
      address = Option(rs.getString("address")),
      name = Option(rs.getString("name")),
      isPrimary = rs.getBoolean("is_primary"),
      metadata = rs.getString("metadata"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      createdBy = rs.getObject("created_by").asInstanceOf[UUID],
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime,
      updatedBy = Option(rs.getObject("updated_by")).map(_.asInstanceOf[UUID])
    )
  }

  private def extractHomeWithOwnership(rs: ResultSet): HomeWithOwnership = {
    val home = Home(
      id = rs.getObject("id").asInstanceOf[UUID],
      address = Option(rs.getString("address")),
      name = Option(rs.getString("name")),
      isPrimary = rs.getBoolean("is_primary"),
      metadata = rs.getString("metadata"),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      createdBy = rs.getObject("created_by").asInstanceOf[UUID],
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime,
      updatedBy = Option(rs.getObject("updated_by")).map(_.asInstanceOf[UUID])
    )

    HomeWithOwnership(
      home = home,
      userRole = rs.getString("role"),
      totalItems = rs.getInt("total_items"),
      totalPhotos = rs.getInt("total_photos"),
      emergencyItems = rs.getInt("emergency_items")
    )
  }
}