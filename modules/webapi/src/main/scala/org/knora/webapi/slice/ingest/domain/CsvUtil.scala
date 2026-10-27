/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.domain

object CsvUtil {
  def escapeCsvValue(value: String): String =
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) s"\"${value.replace("\"", "\"\"")}\""
    else value
}
