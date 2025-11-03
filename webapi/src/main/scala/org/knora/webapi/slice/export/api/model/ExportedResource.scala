/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import org.knora.webapi.slice.infrastructure.ColumnDef
import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ExportedResource(
  resourceIri: String,
  properties: Map[String, String],
)

object ExportedResource {
  given CsvRowBuilder[ExportedResource] = CsvRowBuilder.fromColumnDefs[ExportedResource](
    ColumnDef("Resource IRI", _.resourceIri),
    ColumnDef("props", _.properties),
  )
}
