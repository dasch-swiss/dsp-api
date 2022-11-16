package org.knora.webapi.domain.resource

import org.knora.webapi.config.AppConfig
import org.knora.webapi.{IRI, OntologySchema, SchemaOption}
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import zio.{UIO, ZIO}
import zio.json._

import java.time.Instant

final case class ListResponseDto private(resources: List[ResourceInfoDto], count: Int) extends KnoraResponseV2 {
  override def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption],
    appConfig: AppConfig
  ): String = this.toJson
}
object ListResponseDto {
  def apply(list: List[ResourceInfoDto]): ListResponseDto =
    ListResponseDto(list, list.size)

  implicit val encoder: JsonEncoder[ListResponseDto] =
    DeriveJsonEncoder.gen[ListResponseDto]
}

final case class ResourceInfoDto private (
  resourceIri: IRI,
  creationDate: Instant,
  modificationDate: Option[Instant],
  isDeleted: Boolean
)
object ResourceInfoDto {
  def apply(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(info.iri, info.creationDate, info.modificationDate, info.isDeleted)

  implicit val encoder: JsonEncoder[ResourceInfoDto] =
    DeriveJsonEncoder.gen[ResourceInfoDto]
}

trait RestResourceInfoService {
  def findByResourceClass(projectIri: IRI, resourceClass: IRI): UIO[ListResponseDto]
}
object RestResourceInfoService {
  def findByResourceClass(projectIri: IRI, resourceClass: IRI): ZIO[RestResourceInfoService, Nothing, ListResponseDto] =
    ZIO.service[RestResourceInfoService].flatMap(_.findByResourceClass(projectIri, resourceClass))
}
