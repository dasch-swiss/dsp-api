package org.knora.webapi.domain.resource

import org.knora.webapi.IRI
import zio.{UIO, ZIO}

import java.time.Instant

case class ResourceInfo(iri: IRI, creationDate: Instant, modificationDate: Option[Instant], isDeleted: Boolean)

trait ResourceInfoRepo {
  def findByResourceClass(projectIri: IRI, resourceClass: IRI): UIO[List[ResourceInfo]]
}

object ResourceInfoRepo {

  def findByResourceClass(projectIri: IRI, resourceClass: IRI): ZIO[ResourceInfoRepo, Throwable, List[ResourceInfo]] =
    ZIO.service[ResourceInfoRepo].flatMap(_.findByResourceClass(projectIri, resourceClass))
}
