package com.tuachotu.model.db

import java.time.LocalDateTime
import java.util.UUID

case class Photo(
  id: UUID,
  homeId: Option[UUID],
  homeItemId: Option[UUID], 
  userId: Option[UUID],
  s3Key: String,
  fileName: Option[String],
  contentType: Option[String],
  caption: Option[String],
  isPrimary: Boolean,
  createdBy: Option[UUID],
  createdAt: LocalDateTime
)

case class HomeItem(
  id: UUID,
  homeId: UUID,
  name: String,
  itemType: String,
  isEmergency: Boolean,
  data: String, // JSONB as String
  createdBy: Option[UUID],
  createdAt: LocalDateTime
)

case class PhotoWithDetails(
  photo: Photo,
  homeItem: Option[HomeItem],
  uploadedBy: Option[User]
)