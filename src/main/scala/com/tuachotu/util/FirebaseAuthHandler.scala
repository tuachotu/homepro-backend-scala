package com.tuachotu.util

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.{FirebaseAuth, FirebaseAuthException}
import com.tuachotu.conf.Constant

import java.io.FileInputStream
import scala.jdk.CollectionConverters.*
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger


object FirebaseAuthHandler {
  implicit private val logger: Logger = LoggerUtil.getLogger(classOf[FirebaseAuthHandler.type])
  val serviceAccount = new FileInputStream("/home/ec2-user/data/home-owners-tech-firebase-adminsdk-3jzg4-dea89a3125.json")
  //val serviceAccount = new FileInputStream("/Users/vikrantsingh/Downloads/home-owners-tech-firebase-adminsdk-3jzg4-dea89a3125.json")

  val options = FirebaseOptions.builder()
    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
    .build()

  // Ensure FirebaseApp is initialized only once
  if (FirebaseApp.getApps.isEmpty) {
    FirebaseApp.initializeApp(options)
  }

  // Validate Token
  def validateToken(token: String): Either[String, Map[String, Any]] = {
    try {
      val decodedToken = FirebaseAuth.getInstance().verifyIdToken(token)
      // Extract claims from the decoded token
      val claims = decodedToken.getClaims.asInstanceOf[java.util.Map[String, Any]]
      //LoggerUtil.info(Constant.RequestFireBaseAuthPassed, "claims", claims)
      Right(claims.asScala.toMap) // Convert Java Map to Scala Map
    } catch {
      case e: FirebaseAuthException =>
        LoggerUtil.error(Constant.RequestFireBaseAuthPassed, "message", e.getMessage)
        Left(s"Firebase Auth error: ${e.getMessage}")
      case e: Exception =>
        Left(s"Unexpected error: ${e.getMessage}")
    }
  }
}