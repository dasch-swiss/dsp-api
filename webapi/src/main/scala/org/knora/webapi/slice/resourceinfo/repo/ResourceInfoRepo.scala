package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo.{Order, OrderBy}
import zio.{UIO, ZIO}

trait ResourceInfoRepo {

  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): UIO[List[ResourceInfo]]
}

object ResourceInfoRepo {

  sealed trait OrderBy
  case object creationDate         extends OrderBy
  case object lastModificationDate extends OrderBy

  sealed trait Order
  case object ASC  extends Order
  case object DESC extends Order

  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): ZIO[ResourceInfoRepo, Throwable, List[ResourceInfo]] =
    ZIO
      .service[ResourceInfoRepo]
      .flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass, ordering))
}
