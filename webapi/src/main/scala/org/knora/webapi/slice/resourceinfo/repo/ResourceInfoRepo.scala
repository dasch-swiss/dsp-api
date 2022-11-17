package org.knora.webapi.slice.resourceinfo.repo

import org.knora.webapi.IRI
import zio.{UIO, ZIO}

trait ResourceInfoRepo {
  def findByProjectAndResourceClass(projectIri: IRI, resourceClass: IRI): UIO[List[ResourceInfo]]
}

object ResourceInfoRepo {

  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI
  ): ZIO[ResourceInfoRepo, Throwable, List[ResourceInfo]] =
    ZIO.service[ResourceInfoRepo].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass))
}
