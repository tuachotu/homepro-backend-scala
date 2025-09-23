package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{HomeItem, HomeItemEnhanced, HomeItemType}
import com.tuachotu.model.response.HomeStatsResponse
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

class HomeItemRepository()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  def createHomeItem(homeItem: HomeItem): Future[HomeItem] = {
    val sql = """
      INSERT INTO home_items (id, home_id, name, type, is_emergency, data, created_by, created_at) 
      VALUES (?, ?, ?, ?::home_item_type, ?, ?::jsonb, ?, ?)
    """

    val params = List(
      homeItem.id,
      homeItem.homeId,
      homeItem.name,
      HomeItemType.toString(homeItem.itemType),
      homeItem.isEmergency,
      homeItem.data,
      homeItem.createdBy.orNull,
      Timestamp.valueOf(homeItem.createdAt)
    )

    DatabaseConnection.executeUpdate(sql, params*).map { rowsAffected =>
      if (rowsAffected > 0) {
        logger.info(s"Created home item with ID: ${homeItem.id} for home: ${homeItem.homeId}")
        homeItem
      } else {
        throw new RuntimeException("Failed to create home item")
      }
    }
  }

  def findItemsByHomeId(
    homeId: UUID, 
    itemType: Option[String] = None,
    emergency: Option[Boolean] = None,
    limit: Int = 50,
    offset: Int = 0
  ): Future[List[HomeItemEnhanced]] = {
    // For simplicity, let's start with a basic query and add filters if needed
    val sql = if (itemType.isEmpty && emergency.isEmpty) {
      """
        SELECT 
          hi.id, hi.home_id, hi.name, hi.type, hi.is_emergency, hi.data, 
          hi.created_by, hi.created_at,
          COALESCE(photo_count.count, 0) as photo_count,
          pp.s3_key as primary_s3_key
        FROM home_items hi
        LEFT JOIN (
          SELECT home_item_id, COUNT(*) as count
          FROM photos
          WHERE home_item_id IS NOT NULL
          GROUP BY home_item_id
        ) photo_count ON hi.id = photo_count.home_item_id
        LEFT JOIN photos pp ON hi.id = pp.home_item_id AND pp.is_primary = true
        WHERE hi.home_id = ?
        ORDER BY hi.is_emergency DESC, hi.created_at DESC 
        LIMIT ? OFFSET ?
      """
    } else {
      // Build dynamic query for complex filtering
      buildDynamicQuery(itemType, emergency)
    }

    val params = buildParams(homeId, itemType, emergency, limit, offset)
    
    DatabaseConnection.executeQuery(sql, params*)(extractHomeItemEnhanced).map { items =>
      logger.info(s"Found ${items.length} items for home ID: $homeId")
      items
    }
  }
  
  private def buildDynamicQuery(itemType: Option[String], emergency: Option[Boolean]): String = {
    val baseQuery = """
      SELECT 
        hi.id, hi.home_id, hi.name, hi.type, hi.is_emergency, hi.data, 
        hi.created_by, hi.created_at,
        COALESCE(photo_count.count, 0) as photo_count,
        pp.s3_key as primary_s3_key
      FROM home_items hi
      LEFT JOIN (
        SELECT home_item_id, COUNT(*) as count
        FROM photos
        WHERE home_item_id IS NOT NULL
        GROUP BY home_item_id
      ) photo_count ON hi.id = photo_count.home_item_id
      LEFT JOIN photos pp ON hi.id = pp.home_item_id AND pp.is_primary = true
      WHERE hi.home_id = ?
    """
    
    val filters = scala.collection.mutable.ListBuffer[String]()
    
    itemType.foreach(_ => filters += "AND hi.type = ?")
    emergency.foreach(_ => filters += "AND hi.is_emergency = ?")
    
    baseQuery + filters.mkString(" ") + " ORDER BY hi.is_emergency DESC, hi.created_at DESC LIMIT ? OFFSET ?"
  }
  
  private def buildParams(homeId: UUID, itemType: Option[String], emergency: Option[Boolean], limit: Int, offset: Int): List[Any] = {
    val params = scala.collection.mutable.ListBuffer[Any](homeId)
    itemType.foreach(params += _)
    emergency.foreach(params += _)
    params += limit
    params += offset
    params.toList
  }

  def findItemById(itemId: UUID): Future[Option[HomeItemEnhanced]] = {
    val sql = """
      SELECT 
        hi.id, hi.home_id, hi.name, hi.type, hi.is_emergency, hi.data, 
        hi.created_by, hi.created_at,
        COALESCE(photo_count.count, 0) as photo_count,
        pp.s3_key as primary_s3_key
      FROM home_items hi
      LEFT JOIN (
        SELECT home_item_id, COUNT(*) as count
        FROM photos
        WHERE home_item_id IS NOT NULL
        GROUP BY home_item_id
      ) photo_count ON hi.id = photo_count.home_item_id
      LEFT JOIN photos pp ON hi.id = pp.home_item_id AND pp.is_primary = true
      WHERE hi.id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, itemId)(extractHomeItemEnhanced)
  }

  def getItemStats(homeId: UUID): Future[HomeStatsResponse] = {
    val sql = """
      SELECT
        COUNT(*) as total_items,
        COUNT(CASE WHEN is_emergency THEN 1 END) as emergency_items,
        COALESCE(photo_stats.total_photos, 0) as total_photos
      FROM home_items hi
      LEFT JOIN (
        SELECT
          COUNT(DISTINCT p1.id) as total_photos
        FROM photos p1
        WHERE p1.home_id = ? OR p1.home_item_id IN (
          SELECT id FROM home_items WHERE home_id = ?
        )
      ) photo_stats ON 1=1
      WHERE hi.home_id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, homeId, homeId, homeId)(rs =>
      HomeStatsResponse(
        total_items = rs.getInt("total_items"),
        total_photos = rs.getInt("total_photos"),
        emergency_items = rs.getInt("emergency_items")
      )
    ).map(_.getOrElse(HomeStatsResponse(0, 0, 0)))
  }

  def deletePhotosByHomeItemId(homeItemId: UUID): Future[Unit] = {
    val sql = """
      DELETE FROM photos
      WHERE home_item_id = ?
    """

    DatabaseConnection.executeUpdate(sql, homeItemId).map { rowsAffected =>
      logger.info(s"Deleted $rowsAffected photos for home item $homeItemId")
    }
  }

  def deleteHomeItem(homeItemId: UUID): Future[Unit] = {
    val sql = """
      DELETE FROM home_items
      WHERE id = ?
    """

    DatabaseConnection.executeUpdate(sql, homeItemId).map { rowsAffected =>
      if (rowsAffected == 0) {
        throw new RuntimeException(s"Home item $homeItemId not found")
      }
      logger.info(s"Successfully deleted home item $homeItemId")
    }
  }

  private def extractHomeItemEnhanced(rs: ResultSet): HomeItemEnhanced = {
    HomeItemEnhanced(
      id = rs.getObject("id").asInstanceOf[UUID],
      homeId = rs.getObject("home_id").asInstanceOf[UUID],
      name = rs.getString("name"),
      itemType = rs.getString("type"),
      isEmergency = rs.getBoolean("is_emergency"),
      data = rs.getString("data"),
      createdBy = Option(rs.getObject("created_by")).map(_.asInstanceOf[UUID]),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      photoCount = rs.getInt("photo_count"),
      primaryS3Key = Option(rs.getString("primary_s3_key"))
    )
  }
}