package com.tuachotu.util

import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}

object LoggerUtil {
  type Logger = org.slf4j.Logger

  // Implicitly initialize a logger for the given class
  implicit def getLogger(implicit clazz: Class[?]): Logger = LoggerFactory.getLogger(clazz)

  // Info log with implicit logger and varargs key-value pairs
  def info(message: String, args: Any*)(implicit logger: Logger): Unit = {
    val structuredArgs = createStructuredArguments(args)
    logger.info(message, structuredArgs*)
  }

  // Error log with implicit logger and varargs key-value pairs
  def error(message: String, args: Any*)(implicit logger: Logger): Unit = {
    val structuredArgs = createStructuredArguments(args)
    logger.error(message, structuredArgs*)
  }

  // Debug log with implicit logger and varargs key-value pairs
  def debug(message: String, args: Any*)(implicit logger: Logger): Unit = {
    val structuredArgs = createStructuredArguments(args)
    logger.debug(message, structuredArgs*)
  }

  // Helper function to create structured arguments
  private def createStructuredArguments(args: Seq[Any]): Seq[Any] = {
    require(args.length % 2 == 0, "Arguments must be in key-value pairs")
    args.grouped(2).flatMap {
      case Seq(key: String, value) => Seq(key, StructuredArguments.value(key, value))
      case _ => throw new IllegalArgumentException("Invalid key-value pair format")
    }.toSeq
  }
}