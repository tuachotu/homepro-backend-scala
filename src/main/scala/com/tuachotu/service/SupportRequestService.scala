
package com.tuachotu.service

import com.tuachotu.model.db.{SupportRequest, SupportRequests}

import com.tuachotu.repository.SupportRequestRepository
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

import scala.concurrent.ExecutionContext.Implicits.global

class SupportRequestService(supportRequestRepository: SupportRequestRepository) {
  def supportRequestById(id: UUID): Future[Option[SupportRequest]] =
    supportRequestRepository.findById(id)
}

