package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.UserRoles
import java.util.UUID
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class UserRoleRepository(implicit ec: ExecutionContext) {

  private val userRoles = TableQuery[UserRoles]

  /**
   * Find all role IDs for a given user ID.
   *
   * @param userId UUID of the user.
   * @return A future containing a sequence of role IDs.
   */
  def findRoleIdsByUserId(userId: UUID): Future[Seq[Int]] = {
    DatabaseConnection.db.run(
      userRoles.filter(_.userId === userId).map(_.roleId).result
    )
  }

}