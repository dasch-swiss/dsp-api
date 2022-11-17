package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.IRI
import zio.{UIO, ZIO}

trait RestResourceInfoService {
  def findByProjectAndResourceClass(projectIri: IRI, resourceClass: IRI): UIO[ListResponseDto]
}

object RestResourceInfoService {
  def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI
  ): ZIO[RestResourceInfoService, Nothing, ListResponseDto] =
    ZIO.service[RestResourceInfoService].flatMap(_.findByProjectAndResourceClass(projectIri, resourceClass))
}
