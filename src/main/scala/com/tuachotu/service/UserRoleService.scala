package com.tuachotu.service

import com.tuachotu.repository.UserRoleRepository
import com.tuachotu.repository.RoleRepository

import scala.concurrent.{ExecutionContext, Future}
import java.util.UUID

class UserRoleService(userRoleRepository: UserRoleRepository, roleRepository: RoleRepository)(implicit ec: ExecutionContext) {

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
}