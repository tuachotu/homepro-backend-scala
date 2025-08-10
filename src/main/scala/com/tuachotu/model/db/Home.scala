package com.tuachotu.model.db

import java.time.LocalDateTime
import java.util.UUID

case class Home(
  id: UUID,
  address: Option[String],
  createdAt: LocalDateTime,
  createdBy: UUID,
  updatedAt: LocalDateTime,
  updatedBy: Option[UUID]
)

case class HomeOwner(
  homeId: UUID,
  userId: UUID,
  role: String,
  addedAt: LocalDateTime
)

case class HomeWithOwnership(
  home: Home,
  userRole: String,
  totalItems: Int,
  totalPhotos: Int,
  emergencyItems: Int
)

case class HomeItemEnhanced(
  id: UUID,
  homeId: UUID,
  name: String,
  itemType: String,
  isEmergency: Boolean,
  data: String, // JSONB as String
  createdBy: Option[UUID],
  createdAt: LocalDateTime,
  photoCount: Int,
  primaryS3Key: Option[String]
)