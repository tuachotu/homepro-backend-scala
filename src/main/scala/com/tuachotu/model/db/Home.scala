  package com.tuachotu.model.db

import java.time.LocalDateTime
import java.util.UUID

enum HomeItemType:
  case Room, UtilityControl, Appliance, Structural, Observation, Wiring, Sensor, Other

object HomeItemType:
  def fromString(value: String): HomeItemType = value.toLowerCase match
    case "room" => Room
    case "utility_control" => UtilityControl
    case "appliance" => Appliance
    case "structural" => Structural
    case "observation" => Observation
    case "wiring" => Wiring
    case "sensor" => Sensor
    case "other" => Other
    case _ => throw new IllegalArgumentException(s"Invalid home item type: $value")
  
  def toString(itemType: HomeItemType): String = itemType match
    case Room => "room"
    case UtilityControl => "utility_control"
    case Appliance => "appliance"
    case Structural => "structural"
    case Observation => "observation"
    case Wiring => "wiring"
    case Sensor => "sensor"
    case Other => "other"

case class Home(
  id: UUID,
  address: Option[String],
  name: Option[String],
  isPrimary: Boolean,
  metadata: String,
  createdAt: LocalDateTime,
  createdBy: UUID,
  updatedAt: LocalDateTime,
  updatedBy: Option[UUID]
)

case class HomeItem(
  id: UUID,
  homeId: UUID,
  name: String,
  itemType: HomeItemType,
  isEmergency: Boolean,
  data: String,
  createdBy: Option[UUID],
  createdAt: LocalDateTime
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
  emergencyItems: Int,
  totalNotes: Int
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
  primaryS3Key: Option[String],
  noteCount: Int
)