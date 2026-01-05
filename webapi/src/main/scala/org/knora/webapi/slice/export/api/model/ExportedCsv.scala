/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ExportedCsv(
  headers: List[String],
  rows: List[ExportedResource],
) {
  def rowBuilder: CsvRowBuilder[ExportedResource] =
    new CsvRowBuilder[ExportedResource] {
      def header: Seq[String]                     = headers
      def values(row: ExportedResource): Seq[Any] = row.properties.values.toList
    }
}
