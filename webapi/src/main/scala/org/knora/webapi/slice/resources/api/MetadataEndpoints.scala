/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.tapir.*
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.server.PartialServerEndpoint
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import java.time.Instant
import scala.concurrent.Future

import dsp.errors.RequestRejectedException
import org.knora.webapi.slice.admin.api.AdminPathVariables
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectShortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.infrastructure.ColumnDef
import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ResourceMetadataDto(
  resourceClassIri: String,
  resourceIri: String,
  arkUrl: String,
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
    ColumnDef("Resource IRI", _.resourceIri),
    ColumnDef("ARK URL (Permalink)", _.arkUrl),
    ColumnDef("Resource Class", _.resourceClassIri),
    ColumnDef("Label", _.label),
    ColumnDef("Created by", _.resourceCreatorIri),
    ColumnDef("Creation Date", _.resourceCreationDate),
    ColumnDef("Last Modification Date (if available)", _.resourceLastModificationDate.getOrElse("")),
    ColumnDef("Deletion Date (if available)", _.resourceDeletionDate.getOrElse("")),
  )
}

enum ExportFormat {
  case CSV  extends ExportFormat
  case TSV  extends ExportFormat
  case JSON extends ExportFormat
}
object ExportFormat {
  given PlainCodec[ExportFormat] = Codec.derivedEnumeration[String, ExportFormat].defaultStringBased
}

final case class MetadataEndpoints(private val baseEndpoints: BaseEndpoints) {
  val base = "v2" / "metadata"

  val getResourcesMetadata = baseEndpoints.securedEndpoint.get
    .in(base / "projects" / projectShortcode / "resources")
    .in(query[ExportFormat]("format").default(ExportFormat.CSV))
    .out(header[String]("Content-Type"))
    .out(header[String]("Content-Disposition"))
    .out(stringBody)
    .description(
      "Get metadata of all resources in a project. " +
        "This endpoint is only available for system and project admins.",
    )

  val endpoints: Seq[AnyEndpoint] = (Seq(
    getResourcesMetadata,
  ).map(_.endpoint)).map(_.tag("V2 Metadata"))
}
object MetadataEndpoints {
  val layer = ZLayer.derive[MetadataEndpoints]
}
