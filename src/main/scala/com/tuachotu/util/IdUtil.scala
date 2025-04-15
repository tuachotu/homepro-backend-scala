package com.tuachotu.util

import java.util.UUID
import com.tuachotu.util.TimeUtil

object IdUtil {
  def defaultSupportReqTitle(userId: UUID): String = userId.toString + "_" + TimeUtil.currentEpoch 
}
