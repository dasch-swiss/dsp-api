/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import org.knora.webapi.slice.infrastructure.ColumnDef
import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ExportedResource(
  a: String,
  b: String,
)

object ExportedResource {
  given CsvRowBuilder[ExportedResource] = CsvRowBuilder.fromColumnDefs[ExportedResource](
    ColumnDef("Amorphous", _.a),
    ColumnDef("Bulletin", _.b),
    // ColumnDef("Label", _.label),
    // ColumnDef("Resource Class", _.resourceClassIri),
    // ColumnDef("ARK URL (Permalink)", _.arkUrl),
    // ColumnDef("ARK with timestamp", _.arkUrlWithTimestamp),
    // ColumnDef("Resource IRI", _.resourceIri),
    // ColumnDef("Created by", _.resourceCreatorIri),
    // ColumnDef("Creation Date", _.resourceCreationDate),
    // ColumnDef("Last Modification Date (if available)", _.resourceLastModificationDate.getOrElse("")),
    // ColumnDef("Deletion Date (if available)", _.resourceDeletionDate.getOrElse("")),
  )
}
