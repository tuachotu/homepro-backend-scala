package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection

import com.tuachotu.model.{Role, Roles}
import java.util.UUID
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class RoleRepository(implicit ec: ExecutionContext) {
  // Reference to the roles table
  private val roles = TableQuery[Roles]

  def findAll(): Future[Seq[Role]] = {
    DatabaseConnection.db.run(roles.result)
  }

  // Find a role by its ID
  def findById(id: Int): Future[Option[Role]] = {
    println(3333)
    DatabaseConnection.db.run(roles.filter(_.id === id).result.headOption)
  }
  
// Uncomment as needed
  //  // Create a new role
//  def create(role: Role): Future[Int] = {
//    DatabaseConnection.db.run(roles += role)
//  }
//
//  // Update an existing role
//  def update(id: UUID, updatedRole: Role): Future[Int] = {
//    DatabaseConnection.db.run(
//      roles.filter(_.id === id).update(updatedRole)
//    )
//  }
//
//  // Soft delete a role (set deleted_at)
//  def softDelete(id: UUID): Future[Int] = {
//    DatabaseConnection.db.run(
//      roles.filter(_.id === id)
//        .map(_.deletedAt)
//        .update(Some(new java.sql.Timestamp(System.currentTimeMillis())))
//    )
//  }
}
