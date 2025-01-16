package com.tuachotu.db

import com.tuachotu.util.ConfigUtil
import slick.jdbc.JdbcBackend._
object DatabaseConnection {
  lazy val db = Database.forConfig("slick.dbs.default")
}
