package com.tuachotu.util
import com.typesafe.config.{Config, ConfigFactory}

object ConfigUtil {
  private val config: Config = ConfigFactory.load() // Automatically loads `reference.conf`

  /** Get a string value from the config */
  def getString(path: String, default: String = ""): String =
    if (config.hasPath(path)) config.getString(path) else default

  /** Get an integer value from the config */
  def getInt(path: String, default: Int = 0): Int =
    if (config.hasPath(path)) config.getInt(path) else default

  /** Get a boolean value from the config */
  def getBoolean(path: String, default: Boolean = false): Boolean =
    if (config.hasPath(path)) config.getBoolean(path) else default

  /** Get a nested configuration object */
  def getConfig(path: String): Config =
    config.getConfig(path)
}