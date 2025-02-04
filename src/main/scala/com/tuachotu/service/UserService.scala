package com.tuachotu.service

import com.tuachotu.model.db.User
import com.tuachotu.repository.UserRepository
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global

class UserService(userRepository: UserRepository) {
  def userById(id: UUID): Future[Option[User]] =
    userRepository.findById(id)
  
  def findByFirebaseId(id: String): Future[Option[User]] =
    userRepository.findByFirebaseId(id)
}