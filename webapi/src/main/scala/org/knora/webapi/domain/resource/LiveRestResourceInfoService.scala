package org.knora.webapi.domain.resource

import org.knora.webapi.IRI
import zio.{UIO, ZIO, ZLayer}

final case class LiveRestResourceInfoService(repo: ResourceInfoRepo) extends RestResourceInfoService {
  override def findByResourceClass(projectIri: IRI, resourceClass: IRI): UIO[ListResponseDto] =
    for {
      result <- repo.findByResourceClass(projectIri, resourceClass).map(_.map(ResourceInfoDto(_)))
    } yield ListResponseDto(result)
}

object LiveRestResourceInfoService {
  val layer: ZLayer[ResourceInfoRepo, Nothing, LiveRestResourceInfoService] =
    ZLayer.fromZIO(ZIO.service[ResourceInfoRepo].map(new LiveRestResourceInfoService(_)))
}
