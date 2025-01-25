package com.tuachotu.service

import com.tuachotu.model.Role
import com.tuachotu.repository.RoleRepository
import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class RoleService(roleRepository: RoleRepository) {
  def findById(id: Int): Future[Option[Role]] =
    roleRepository.findById(id)

  def findAll(): Future[Seq[Role]] =
    roleRepository.findAll()
}