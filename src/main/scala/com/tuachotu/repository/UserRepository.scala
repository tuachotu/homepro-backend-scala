package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.{User, Users}
import java.util.UUID
//import java.sql.Timestamp
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class UserRepository(implicit ec: ExecutionContext) {
  private val users = TableQuery[Users]

  def findAll(): Future[Seq[User]] = {
    DatabaseConnection.db.run(users.result)
  }

  def findById(id: UUID): Future[Option[User]] = {
    DatabaseConnection.db.run(users.filter(_.id === id).result.headOption)
  }

  def create(user: User): Future[Int] = {
    DatabaseConnection.db.run(users += user)
  }
}