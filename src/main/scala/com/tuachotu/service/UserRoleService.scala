package com.tuachotu.service

import com.tuachotu.repository.UserRoleRepository
import com.tuachotu.repository.RoleRepository
import com.tuachotu.util.{LoggerUtil, HomeOwnerException}
import com.tuachotu.util.LoggerUtil.Logger

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

class UserRoleService(userRoleRepository: UserRoleRepository, roleRepository: RoleRepository)(implicit ec: ExecutionContext) {
  implicit private val logger: Logger = LoggerUtil.getLogger(getClass)

  private val DEFAULT_ROLE_NAME = "HO"

  /**
   * Get all role IDs associated with a specific user.
   *
   * @param userId UUID of the user.
   * @return A future containing a sequence of role IDs.
   */
  def getRolesByUserId(userId: UUID): Future[Seq[Int]] = {
    userRoleRepository.findRoleIdsByUserId(userId)
  }

  def getRoleNamesByUserId(userId: UUID): Future[Seq[String]] = {
    for {
      roleIds <- userRoleRepository.findRoleIdsByUserId(userId) // Fetch role IDs
      roles <- Future.sequence(roleIds.map(roleRepository.findById)) // Fetch Role objects
    } yield roles.flatten.map(_.name) // Extract names from Role objects
  }

  /**
   * Assigns the default 'homeowner' role to a new user
   *
   * @param userId UUID of the user to assign the default role to
   * @return Future containing the number of rows affected
   */
  def assignDefaultRole(userId: UUID): Future[Int] = {
    LoggerUtil.info("Assigning default role to new user", "userId", userId.toString, "roleName", DEFAULT_ROLE_NAME)

    roleRepository.findByName(DEFAULT_ROLE_NAME).flatMap {
      case Some(role) =>
        userRoleRepository.assignRoleToUser(userId, role.id).map { rowsAffected =>
          LoggerUtil.info("Default role assigned successfully",
            "userId", userId.toString,
            "roleId", role.id,
            "roleName", DEFAULT_ROLE_NAME,
            "rowsAffected", rowsAffected)
          rowsAffected
        }
      case None =>
        val errorMsg = s"Default role '$DEFAULT_ROLE_NAME' not found in database"
        LoggerUtil.error("Failed to assign default role", "userId", userId.toString, "error", errorMsg)
        Future.failed(new HomeOwnerException(errorMsg))
    }
  }
}