package org.knora.webapi.domain.resource

import org.knora.webapi.config.AppConfig
import org.knora.webapi.{IRI, OntologySchema, SchemaOption}
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import zio.{UIO, ZIO}
import zio.json._

final case class ResourceInfoDtoListResponse(list: List[ResourceInfoDto]) extends KnoraResponseV2 {
  override def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption],
    appConfig: AppConfig
  ): String = this.toJson
}
object ResourceInfoDtoListResponse {
  implicit val encoder: JsonEncoder[ResourceInfoDtoListResponse] =
    DeriveJsonEncoder.gen[ResourceInfoDtoListResponse]
}
final case class ResourceInfoDto(resourceIri: IRI, creationDate: String)

object ResourceInfoDto {
  def fromResourceInfo(info: ResourceInfo): ResourceInfoDto =
    ResourceInfoDto(info.iri, info.creationDate.toString)

  implicit val encoder: JsonEncoder[ResourceInfoDto] =
    DeriveJsonEncoder.gen[ResourceInfoDto]
}

trait RestResourceInfoService {
  def findByResourceClass(resourceClass: IRI): UIO[ResourceInfoDtoListResponse]
}

object RestResourceInfoService {
  def findByResourceClass(resourceClass: IRI): ZIO[RestResourceInfoService, Nothing, ResourceInfoDtoListResponse] =
    ZIO.service[RestResourceInfoService].flatMap(_.findByResourceClass(resourceClass))
}
