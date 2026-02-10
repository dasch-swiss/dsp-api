/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import sttp.tapir.Schema
import zio.json.JsonCodec

enum V3ErrorCode(val template: String):
  // V3ErrorCode.NotFound errors
  case ontology_not_found      extends V3ErrorCode("The ontology with IRI {id} was not found.")
  case project_not_found       extends V3ErrorCode("The project with IRI {id} was not found.")
  case export_not_found        extends V3ErrorCode("The export with ID {id} in project IRI {projectIri} was not found.")
  case import_not_found        extends V3ErrorCode("The import with ID {id} in project IRI {projectIri} was not found.")
  case resourceClass_not_found extends V3ErrorCode("The resource class with IRI {id} was not found.")
  case resource_not_found      extends V3ErrorCode("The resource with IRI {id} was not found.")
  // V3ErrorCode.Conflict errors
  case export_in_progress
      extends V3ErrorCode("The export ID {id} is currently in progress for the project IRI {projectIri}.")
  case import_in_progress
      extends V3ErrorCode("The import ID {id} is currently in progress for the project IRI {projectIri}.")
  // Other error codes can be added here as needed

object V3ErrorCode:

  type NotFounds = export_not_found.type | ontology_not_found.type | project_not_found.type | resource_not_found.type |
    resourceClass_not_found.type | import_not_found.type

  type Conflicts = export_in_progress.type | import_in_progress.type

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
  }
