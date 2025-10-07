/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.model

import sttp.model.MediaType
import zio.json.*

/**
 * Represents a request to export resources of a specific class.
 * When selectedProperties is empty, all properties found on the resources will be exported.
 */
final case class ExportRequest(
  resourceClass: String,
  selectedProperties: Option[List[String]] = None,
  format: ExportFormat = ExportFormat.CSV,
  language: Option[String] = None,
  includeReferenceIris: Boolean = true,
)

object ExportRequest {
  implicit val codec: JsonCodec[ExportRequest] = DeriveJsonCodec.gen[ExportRequest]
}

/**
 * Supported export formats.
 */
enum ExportFormat derives JsonCodec {
  case CSV
  case JSON
}

/**
 * Result of an export operation.
 */
final case class ExportResult(
  data: String,
  mediaType: MediaType,
  filename: String,
)

/**
 * Represents a row of resource data for export.
 */
final case class ResourceExportRow(
  resourceIri: String,
  resourceClass: String,
  projectIri: String,
  properties: Map[String, String],
) {
  def getValue(propertyIri: String): String = properties.getOrElse(propertyIri, "")
}

object ResourceExportRow {
  implicit val codec: JsonCodec[ResourceExportRow] = DeriveJsonCodec.gen[ResourceExportRow]
}

/**
 * Configuration for CSV export.
 */
final case class CsvExportConfig(
  includeHeaders: Boolean = true,
  delimiter: String = ",",
  quoteChar: String = "\"",
)
