package org.knora.webapi.domain.resource

import org.knora.webapi.IRI
import zio.{UIO, ZIO, ZLayer}

final case class LiveRestResourceInfoService(repo: ResourceInfoRepo) extends RestResourceInfoService {
  override def findByResourceClass(resourceClass: IRI): UIO[ResourceInfoDtoListResponse] =
    for {
      infoList <- repo.findByResourceClass(resourceClass)
      r        <- ZIO.succeed(infoList.map(ResourceInfoDto.fromResourceInfo))
    } yield ResourceInfoDtoListResponse(r)
}

object LiveRestResourceInfoService {
  val layer: ZLayer[ResourceInfoRepo, Nothing, LiveRestResourceInfoService] =
    ZLayer.fromZIO(ZIO.service[ResourceInfoRepo].map(new LiveRestResourceInfoService(_)))
}
