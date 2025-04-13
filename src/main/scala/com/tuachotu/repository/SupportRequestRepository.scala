package com.tuachotu.repository

import com.tuachotu.db.DatabaseConnection
import com.tuachotu.model.db.{SupportRequest,SupportRequests }
import java.util.UUID
//import java.sql.Timestamp
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.{ExecutionContext, Future}

class SupportRequestRepository(implicit ec: ExecutionContext) {
  private val supportRequests = TableQuery[SupportRequests]

  
  def findById(id: UUID): Future[Option[SupportRequest]] = {
    DatabaseConnection.db.run(supportRequests.filter(_.id === id).result.headOption)
  }

  def create(supportRequest: SupportRequest): Future[SupportRequest] = {
    val insertQuery = supportRequests returning supportRequests += supportRequest
    DatabaseConnection.db.run(insertQuery)
  }
}
