package com.tuachotu.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TimeUtil {
  def currentEpoch: Long = java.time.Instant.now().toEpochMilli
  
  def formatLocalDateTime(dateTime: LocalDateTime): String = {
    dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
  }
}
