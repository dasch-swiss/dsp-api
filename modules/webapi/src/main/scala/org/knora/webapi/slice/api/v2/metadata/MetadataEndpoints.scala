/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.metadata

import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.Codec.PlainCodec
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import java.time.Instant

import org.knora.webapi.slice.api.admin.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.ColumnDef
import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ResourceMetadataDto(
  resourceClassIri: String,
  resourceIri: String,
  arkUrl: String,
  arkUrlWithTimestamp: String,
  label: String,
  resourceCreatorIri: String,
  resourceCreationDate: Instant,
  resourceLastModificationDate: Option[Instant],
  resourceDeletionDate: Option[Instant],
)
object ResourceMetadataDto {
  given JsonCodec[ResourceMetadataDto] = DeriveJsonCodec.gen[ResourceMetadataDto]
  given Schema[ResourceMetadataDto]    = Schema.derived[ResourceMetadataDto]

  given CsvRowBuilder[ResourceMetadataDto] = CsvRowBuilder.fromColumnDefs[ResourceMetadataDto](
    ColumnDef("Label", _.label),
    ColumnDef("Resource Class", _.resourceClassIri),
    ColumnDef("ARK URL (Permalink)", _.arkUrl),
    ColumnDef("ARK with timestamp", _.arkUrlWithTimestamp),
    ColumnDef("Resource IRI", _.resourceIri),
    ColumnDef("Created by", _.resourceCreatorIri),
    ColumnDef("Creation Date", _.resourceCreationDate),
    ColumnDef("Last Modification Date (if available)", _.resourceLastModificationDate.getOrElse("")),
    ColumnDef("Deletion Date (if available)", _.resourceDeletionDate.getOrElse("")),
  )
}

enum ExportFormat(val mediaType: MediaType, val ext: String) {
  case CSV  extends ExportFormat(MediaType.TextCsv, "csv")
  case TSV  extends ExportFormat(MediaType("text", "tab-separated-values"), "tsv")
  case JSON extends ExportFormat(MediaType.ApplicationJson, "json")
}
object ExportFormat {
  given PlainCodec[ExportFormat] = Codec.derivedEnumeration[String, ExportFormat].defaultStringBased
}

final class MetadataEndpoints(baseEndpoints: BaseEndpoints) {
  val base = "v2" / "metadata"

  val getResourcesMetadata = baseEndpoints.securedEndpoint.get
    .in(base / "projects" / projectShortcode / "resources")
    .in(query[ExportFormat]("format").default(ExportFormat.CSV).description("The format of the response."))
    .in(
      query[List[IriDto]]("classIris")
        .default(List.empty)
        .description(
          "List of resource class IRIs to filter the resources. " +
            "If not present, all resources of the project will be returned.",
        ),
    )
    .out(
      header[MediaType]("Content-Type").description(
        s"The content type of the response, depends on the format: ${ExportFormat.values.map(f => s"$f => ${f.mediaType}").mkString(", ")}",
      ),
    )
    .out(
      header[String]("Content-Disposition").description(
        "Will be set to attachment. " +
          "The filename contains project shortcode, export timestamp and the format.",
      ),
    )
    .out(
      stringBody
        .description(
          s"Depending on the format the response will be rendered as ${ExportFormat.values.mkString(",")}. The example is CSV.",
        )
        .example(
          """
            |Resource IRI,ARK URL (Permalink),Resource Class,Label,Created by,Creation Date,Last Modification Date (if available),Deletion Date (if available)
            |http://rdfh.ch/0803/00014b43f902,http://0.0.0.0:3336/ark:/72163/1/0803/00014b43f902l,http://0.0.0.0:3333/ontology/0803/incunabula/v2#page,t8r,http://rdfh.ch/users/91e19f1e01,2016-03-02T15:05:37Z,,
            |http://rdfh.ch/0803/0015627fe303,http://0.0.0.0:3336/ark:/72163/1/0803/0015627fe303e,http://0.0.0.0:3333/ontology/0803/incunabula/v2#page,m8v,http://rdfh.ch/users/91e19f1e01,2016-03-02T15:05:49Z,,""".stripMargin,
        ),
    )
    .description(
      "Get metadata of all resources in a project. " +
        "The metadata is returned with complex schema IRIs in the payload. " +
        "This endpoint is only available for system and project admins.",
    )
}

object MetadataEndpoints {
  private[metadata] val layer = ZLayer.derive[MetadataEndpoints]
}
