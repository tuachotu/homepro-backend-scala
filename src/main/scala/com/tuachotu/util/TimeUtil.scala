package com.tuachotu.util

object TimeUtil {
  def  currentEpoch: Long = java.time.Instant.now().toEpochMilli

}
