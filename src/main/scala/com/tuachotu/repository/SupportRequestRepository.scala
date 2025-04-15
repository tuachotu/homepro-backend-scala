package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{SupportRequest, SupportRequests}
import com.tuachotu.model.request.CreateSupportRequest
import java.time.LocalDateTime
import slick.jdbc.PostgresProfile

import java.util.UUID

//import java.sql.Timestamp
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class SupportRequestRepository(implicit ec: ExecutionContext) {
  private val supportRequests = TableQuery[SupportRequests]


  def findById(id: UUID): Future[Option[SupportRequest]] = {
    DatabaseConnection.db.run(supportRequests.filter(_.id === id).result.headOption)
  }

  def create(csr: CreateSupportRequest): Future[SupportRequest] = {
    val now = LocalDateTime.now()

    val insertQuery = supportRequests
      .returning(supportRequests)
      .into((_, generated) => generated)

    val insertRow = SupportRequest(
      id = UUID.randomUUID(), // can be null if DB will set it
      homeownerId = csr.homeownerId,
      homeId = csr.homeId,
      title = csr.title,
      description = csr.description,
      status =  "open",
      priority = csr.priority,
      assignedExpertId = csr.assignedExpertId,
      createdAt = now,
      createdBy = csr.homeownerId,
      updatedAt = now,
      updatedBy = None
    )

    DatabaseConnection.db.run(insertQuery += insertRow)
  }
}
