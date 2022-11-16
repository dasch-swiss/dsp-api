package org.knora.webapi.domain.resource

import org.knora.webapi.IRI
import zio.{UIO, ZIO}

case class ResourceInfo(iri: IRI, creationDate: java.time.Instant)

trait ResourceInfoRepo {
  def findByResourceClass(resourceClass: IRI): UIO[List[ResourceInfo]]
}

object ResourceInfoRepo {

  def findByResourceClass(resourceClass: IRI): ZIO[ResourceInfoRepo, Throwable, List[ResourceInfo]] =
    ZIO.service[ResourceInfoRepo].flatMap(_.findByResourceClass(resourceClass))
}
