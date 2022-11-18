package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.IRI
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfo
import zio.json._

import java.time.Instant
final case class ListResponseDto private (resources: List[ResourceInfoDto], count: Int)
object ListResponseDto {
  def apply(list: List[ResourceInfoDto]): ListResponseDto =
    ListResponseDto(list, list.size)

  implicit val encoder: JsonEncoder[ListResponseDto] =
    DeriveJsonEncoder.gen[ListResponseDto]
}

final case class ResourceInfoDto private (
  resourceIri: IRI,
  creationDate: Instant,
  lastModificationDate: Instant,
  deleteDate: Option[Instant],
  isDeleted: Boolean
)
object ResourceInfoDto {
  def apply(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(info.iri, info.creationDate, info.lastModificationDate, info.deleteDate, info.isDeleted)

  implicit val encoder: JsonEncoder[ResourceInfoDto] =
    DeriveJsonEncoder.gen[ResourceInfoDto]
}
