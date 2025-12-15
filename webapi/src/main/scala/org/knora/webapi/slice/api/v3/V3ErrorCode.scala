/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.Schema
import zio.json.JsonCodec

enum V3ErrorCode(val template: String):
  // V3ErrorCode.NotFound errors
  case ontology_not_found       extends V3ErrorCode("The ontology with IRI {id} was not found.")
  case project_not_found        extends V3ErrorCode("The project with IRI {id} was not found.")
  case resourceClass_not_found  extends V3ErrorCode("The resource class with IRI {id} was not found.")
  case resource_not_found       extends V3ErrorCode("The resource with IRI {id} was not found.")
  case invalid_resourceClassIri extends V3ErrorCode("The provided resource class IRI {id} is invalid.")

object V3ErrorCode:

  type NotFounds = ontology_not_found.type | project_not_found.type | resource_not_found.type |
    resourceClass_not_found.type

  type Invalids = invalid_resourceClassIri.type

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
