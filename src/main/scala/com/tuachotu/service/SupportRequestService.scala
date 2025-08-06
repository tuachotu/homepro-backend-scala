
package com.tuachotu.service

import com.tuachotu.model.db.SupportRequest
import com.tuachotu.model.request.CreateSupportRequest
import com.tuachotu.repository.SupportRequestRepository
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global

class SupportRequestService(supportRequestRepository: SupportRequestRepository) {
//  def supportRequestById(id: UUID): Future[Option[SupportRequest]] =
//supportRequestRepository.findById(id)

  def createSupportRequest(csr: CreateSupportRequest): Future[SupportRequest] =
    supportRequestRepository.create(csr)
}



