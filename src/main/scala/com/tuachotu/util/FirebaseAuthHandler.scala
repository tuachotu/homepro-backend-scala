package com.tuachotu.util

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.{FirebaseAuth, FirebaseAuthException}
import com.tuachotu.conf.Constant
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import com.tuachotu.util.ConfigUtil

import java.io.FileInputStream
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

object FirebaseAuthHandler {
  implicit private val logger: Logger = LoggerUtil.getLogger(classOf[FirebaseAuthHandler.type])

  private val serviceAccount = new FileInputStream(ConfigUtil.getString("firebase.auth.config-path"))

  private val options = FirebaseOptions.builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .build()

  // Ensure FirebaseApp is initialized only once
  if (FirebaseApp.getApps.isEmpty) {
    FirebaseApp.initializeApp(options)
  }

  /**
   * Validate the Firebase token asynchronously
   *
   * @param token Firebase ID token
   * @return A future of Either containing an error message or the decoded claims
   */
  def validateTokenAsync(token: String)(implicit ec: ExecutionContext): Future[Either[String, Map[String, Any]]] = {
    Future {
      try {
        val decodedToken = FirebaseAuth.getInstance().verifyIdToken(token)
        val claims = decodedToken.getClaims.asInstanceOf[java.util.Map[String, Any]]
        Right(claims.asScala.toMap) // Convert Java Map to Scala Map
      } catch {
        case e: FirebaseAuthException =>
          LoggerUtil.error(Constant.RequestFireBaseAuthPassed, "message", e.getMessage)
          Left(s"Firebase Auth error: ${e.getMessage}")
        case e: Exception =>
          LoggerUtil.error(Constant.RequestFireBaseAuthPassed, "message", e.getMessage)
          Left(s"Unexpected error: ${e.getMessage}")
      }
    }
  }
}