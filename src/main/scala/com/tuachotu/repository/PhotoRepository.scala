package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{Photo, PhotoWithDetails, HomeItem, HomeItemType, User}
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

class PhotoRepository()(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  def findPhotosByHomeId(homeId: UUID): Future[List[PhotoWithDetails]] = {
    val sql = """
      SELECT 
        p.id, p.home_id, p.home_item_id, p.user_id, p.s3_key, p.file_name, p.content_type, 
        p.caption, p.is_primary, p.created_by, p.created_at,
        hi.id as hi_id, hi.home_id as hi_home_id, hi.name as hi_name, hi.type as hi_type, 
        hi.is_emergency as hi_is_emergency, hi.data as hi_data, hi.created_by as hi_created_by, hi.created_at as hi_created_at,
        u.id as u_id, u.firebase_uid, u.name as u_name, u.email, u.phone_number, u.profile,
        u.created_by as u_created_by, u.created_at as u_created_at, u.last_modified_by, u.last_modified_at, u.deleted_at
      FROM photos p
      LEFT JOIN home_items hi ON p.home_item_id = hi.id
      LEFT JOIN users u ON p.created_by = u.id
      WHERE p.home_id = ?
      ORDER BY p.is_primary DESC, p.created_at DESC
    """

    DatabaseConnection.executeQuery(sql, homeId)(extractPhotoWithDetails)
  }

  def findPhotoById(photoId: UUID): Future[Option[Photo]] = {
    val sql = """
      SELECT 
        id, home_id, home_item_id, user_id, s3_key, file_name, content_type, 
        caption, is_primary, created_by, created_at
      FROM photos 
      WHERE id = ?
    """

    DatabaseConnection.executeQuerySingle(sql, photoId)(extractPhoto)
  }

  def updatePhotoHomeItemId(photoId: UUID, homeItemId: UUID): Future[Int] = {
    val sql = """
      UPDATE photos 
      SET home_item_id = ? 
      WHERE id = ?
    """

    DatabaseConnection.executeUpdate(sql, homeItemId, photoId).map { rowsAffected =>
      logger.info(s"Updated photo $photoId to link with home item $homeItemId")
      rowsAffected
    }
  }

  def findPhotosByHomeItemId(homeItemId: UUID): Future[List[PhotoWithDetails]] = {
    val sql = """
      SELECT 
        p.id, p.home_id, p.home_item_id, p.user_id, p.s3_key, p.file_name, p.content_type, 
        p.caption, p.is_primary, p.created_by, p.created_at,
        hi.id as hi_id, hi.home_id as hi_home_id, hi.name as hi_name, hi.type as hi_type, 
        hi.is_emergency as hi_is_emergency, hi.data as hi_data, hi.created_by as hi_created_by, hi.created_at as hi_created_at,
        u.id as u_id, u.firebase_uid, u.name as u_name, u.email, u.phone_number, u.profile,
        u.created_by as u_created_by, u.created_at as u_created_at, u.last_modified_by, u.last_modified_at, u.deleted_at
      FROM photos p
      LEFT JOIN home_items hi ON p.home_item_id = hi.id
      LEFT JOIN users u ON p.created_by = u.id
      WHERE p.home_item_id = ?
      ORDER BY p.is_primary DESC, p.created_at DESC
    """

    DatabaseConnection.executeQuery(sql, homeItemId)(extractPhotoWithDetails)
  }

  private def extractPhotoWithDetails(rs: ResultSet): PhotoWithDetails = {
    val photo = Photo(
      id = rs.getObject("id").asInstanceOf[UUID],
      homeId = Option(rs.getObject("home_id")).map(_.asInstanceOf[UUID]),
      homeItemId = Option(rs.getObject("home_item_id")).map(_.asInstanceOf[UUID]),
      userId = Option(rs.getObject("user_id")).map(_.asInstanceOf[UUID]),
      s3Key = rs.getString("s3_key"),
      fileName = Option(rs.getString("file_name")),
      contentType = Option(rs.getString("content_type")),
      caption = Option(rs.getString("caption")),
      isPrimary = rs.getBoolean("is_primary"),
      createdBy = Option(rs.getObject("created_by")).map(_.asInstanceOf[UUID]),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime
    )

    val homeItem = Option(rs.getObject("hi_id")).map { _ =>
      HomeItem(
        id = rs.getObject("hi_id").asInstanceOf[UUID],
        homeId = rs.getObject("hi_home_id").asInstanceOf[UUID],
        name = rs.getString("hi_name"),
        itemType = HomeItemType.fromString(rs.getString("hi_type")),
        isEmergency = rs.getBoolean("hi_is_emergency"),
        data = rs.getString("hi_data"),
        createdBy = Option(rs.getObject("hi_created_by")).map(_.asInstanceOf[UUID]),
        createdAt = rs.getTimestamp("hi_created_at").toLocalDateTime
      )
    }

    val uploadedBy = Option(rs.getObject("u_id")).map { _ =>
      User(
        id = rs.getObject("u_id").asInstanceOf[UUID],
        firebaseUid = rs.getString("firebase_uid"),
        name = Option(rs.getString("u_name")),
        email = Option(rs.getString("email")),
        phoneNumber = Option(rs.getString("phone_number")),
        profile = Option(rs.getString("profile")),
        createdBy = Option(rs.getString("u_created_by")),
        createdAt = rs.getTimestamp("u_created_at").toLocalDateTime,
        lastModifiedBy = Option(rs.getString("last_modified_by")),
        lastModifiedAt = rs.getTimestamp("last_modified_at").toLocalDateTime,
        deletedAt = Option(rs.getTimestamp("deleted_at")).map(_.toLocalDateTime)
      )
    }

    PhotoWithDetails(photo, homeItem, uploadedBy)
  }

  private def extractPhoto(rs: ResultSet): Photo = {
    Photo(
      id = rs.getObject("id").asInstanceOf[UUID],
      homeId = Option(rs.getObject("home_id")).map(_.asInstanceOf[UUID]),
      homeItemId = Option(rs.getObject("home_item_id")).map(_.asInstanceOf[UUID]),
      userId = Option(rs.getObject("user_id")).map(_.asInstanceOf[UUID]),
      s3Key = rs.getString("s3_key"),
      fileName = Option(rs.getString("file_name")),
      contentType = Option(rs.getString("content_type")),
      caption = Option(rs.getString("caption")),
      isPrimary = rs.getBoolean("is_primary"),
      createdBy = Option(rs.getObject("created_by")).map(_.asInstanceOf[UUID]),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime
    )
  }
}