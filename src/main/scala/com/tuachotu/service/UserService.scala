package com.tuachotu.service

import com.tuachotu.model.db.User
import com.tuachotu.repository.UserRepository
import com.tuachotu.util.LoggerUtil
import com.tuachotu.util.LoggerUtil.Logger
import java.util.UUID
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global

class UserService(userRepository: UserRepository) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  def userById(id: UUID): Future[Option[User]] =
    userRepository.findById(id)

  def findByFirebaseId(id: String): Future[Option[User]] =
    userRepository.findByFirebaseId(id)

  /**
   * Creates a new user from Firebase authentication claims
   * @param firebaseUid Firebase user ID
   * @param claims Firebase token claims containing user information
   * @return Future containing the newly created User
   */
  def createFromFirebaseClaims(firebaseUid: String, claims: Map[String, Any]): Future[User] = {
    val userId = UUID.randomUUID()
    val now = LocalDateTime.now()

    // Extract user information from Firebase claims
    val email = claims.get("email").map(_.toString)
    val name = claims.get("name").map(_.toString)
    val phoneNumber = claims.get("phone_number").map(_.toString)

    val newUser = User(
      id = userId,
      firebaseUid = firebaseUid,
      name = name,
      email = email,
      phoneNumber = phoneNumber,
      profile = None,
      createdBy = Some("system"),
      lastModifiedBy = Some("system"),
      createdAt = now,
      lastModifiedAt = now,
      deletedAt = None
    )

    LoggerUtil.info("Creating new user from Firebase claims",
      "firebaseUid", firebaseUid,
      "email", email.getOrElse("N/A"),
      "name", name.getOrElse("N/A"))

    userRepository.create(newUser).map { _ =>
      LoggerUtil.info("New user created successfully", "userId", userId.toString)
      newUser
    }
  }
}