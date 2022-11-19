package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo.{ASC, Order, OrderBy, lastModificationDate}
import zio.{UIO, ZIO}

trait RestResourceInfoService {
  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): UIO[ListResponseDto]
}

object RestResourceInfoService {
  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order) = (lastModificationDate, ASC)
  ): ZIO[RestResourceInfoService, Nothing, ListResponseDto] =
    ZIO.service[RestResourceInfoService].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass, ordering))
}
