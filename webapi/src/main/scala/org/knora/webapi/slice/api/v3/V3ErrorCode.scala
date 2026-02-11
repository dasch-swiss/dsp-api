/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import sttp.tapir.Schema
import zio.json.JsonCodec

enum V3ErrorCode(val template: String):
  // V3ErrorCode.NotFound errors
  case ontology_not_found      extends V3ErrorCode("The ontology '{id}' was not found.")
  case project_not_found       extends V3ErrorCode("The project '{id}' was not found.")
  case export_not_found        extends V3ErrorCode("The export '{id}' in project '{projectIri}' was not found.")
  case import_not_found        extends V3ErrorCode("The import '{id}' in project '{projectIri}' was not found.")
  case resourceClass_not_found extends V3ErrorCode("The resource class '{id}' was not found.")
  case resource_not_found      extends V3ErrorCode("The resource with IRI '{id}' was not found.")
  // V3ErrorCode.Conflict errors
  case export_exists      extends V3ErrorCode("Export '{id}' exists for project '{projectIri}'.")
  case export_in_progress extends V3ErrorCode("Export '{id}' in progress for project '{projectIri}'.")
  case export_failed      extends V3ErrorCode("Export '{id}' failed for project '{projectIri}'.")
  case import_exists      extends V3ErrorCode("Import '{id}' exists for project '{projectIri}'.")
  case import_in_progress extends V3ErrorCode("Import '{id}' in progress for project '{projectIri}'.")
  // Other error codes can be added here as needed

object V3ErrorCode:

  type NotFounds = export_not_found.type | ontology_not_found.type | project_not_found.type | resource_not_found.type |
    resourceClass_not_found.type | import_not_found.type

  type Conflicts = export_exists.type | export_failed.type | export_in_progress.type | import_exists.type |
    import_in_progress.type

  given Schema[V3ErrorCode] = Schema.derivedEnumeration[V3ErrorCode].defaultStringBased

  given JsonCodec[V3ErrorCode] = JsonCodec.string
    .transformOrFail(
      str => V3ErrorCode.values.find(_.toString == str).toRight(s"Unknown V3ErrorCode: $str"),
      _.toString.toLowerCase,
    )

  extension (code: V3ErrorCode) {
    def description: String =
      s"""Example template string for code `$code`:
         |```
         |${code.template}
         |```""".stripMargin

    def mkExampleMap: Map[String, String] = {
      // Extract placeholders from the template and create an example map
      val placeholderPattern = "\\{(\\w+)\\}".r
      placeholderPattern
        .findAllMatchIn(code.template)
        .map { m =>
          val key = m.group(1)
          key -> s"example_$key"
        }
        .toMap
    }
  }
