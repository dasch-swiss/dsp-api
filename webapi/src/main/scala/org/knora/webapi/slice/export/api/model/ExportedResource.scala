/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import scala.collection.immutable.ListMap

import org.knora.webapi.slice.infrastructure.CsvRowBuilder

final case class ExportedResource(
  label: String,
  resourceIri: String,
  properties: ListMap[String, String],
)

object ExportedResource {
  def rowBuilder(headers: List[String]): CsvRowBuilder[ExportedResource] =
    new CsvRowBuilder[ExportedResource] {
      def header: Seq[String]                     = headers
      def values(row: ExportedResource): Seq[Any] =
        row.label +: row.resourceIri +: row.properties.values.toList
    }
}
