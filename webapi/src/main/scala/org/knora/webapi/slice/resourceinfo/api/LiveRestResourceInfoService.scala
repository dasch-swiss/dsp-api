package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo.{Order, OrderBy}
import zio.{UIO, ZIO, ZLayer}

final case class LiveRestResourceInfoService(repo: ResourceInfoRepo) extends RestResourceInfoService {
  override def findByProjectAndResourceClass(
    projectIri: IRI,
    resourceClass: IRI,
    ordering: (OrderBy, Order)
  ): UIO[ListResponseDto] =
    for {
      result <- repo.findByProjectAndResourceClass(projectIri, resourceClass, ordering).map(_.map(ResourceInfoDto(_)))
    } yield ListResponseDto(result)
}

object LiveRestResourceInfoService {
  val layer: ZLayer[ResourceInfoRepo, Nothing, LiveRestResourceInfoService] =
    ZLayer.fromZIO(ZIO.service[ResourceInfoRepo].map(new LiveRestResourceInfoService(_)))
}
