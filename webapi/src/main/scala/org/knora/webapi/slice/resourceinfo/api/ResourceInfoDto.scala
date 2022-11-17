package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.resourceinfo.repo.ResourceInfo
import org.knora.webapi.{IRI, OntologySchema, SchemaOption}
import zio.json._

import java.time.Instant
final case class ListResponseDto private (resources: List[ResourceInfoDto], count: Int)
object ListResponseDto {
  def apply(list: List[ResourceInfoDto]): ListResponseDto =
    ListResponseDto(list, list.size)

  implicit val encoder: JsonEncoder[ListResponseDto] =
    DeriveJsonEncoder.gen[ListResponseDto]
}

object ResourceInfoDto {
  def apply(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(info.iri, info.creationDate, info.modificationDate, info.isDeleted)

  implicit val encoder: JsonEncoder[ResourceInfoDto] =
    DeriveJsonEncoder.gen[ResourceInfoDto]
}

final case class ResourceInfoDto private (
  resourceIri: IRI,
  creationDate: Instant,
  modificationDate: Option[Instant],
  isDeleted: Boolean
)
