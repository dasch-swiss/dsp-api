/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.model.StatusCode
import sttp.tapir.EndpointIO.Example
import sttp.tapir.EndpointOutput
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.oneOfVariant
import sttp.tapir.statusCode

trait EndpointHelper {
  def notFoundVariant(codes: V3ErrorCode.NotFounds*): EndpointOutput.OneOfVariant[NotFound] =
    oneOfVariant(
      statusCode(StatusCode.NotFound).and(
        jsonBody[NotFound].examples(codes.toList.map(mkExampleNotFound)),
      ),
    )

  def badRequestVariant: EndpointOutput.OneOfVariant[BadRequest] =
    oneOfVariant(
      statusCode(StatusCode.BadRequest).and(jsonBody[BadRequest].example(BadRequest("Bad request example message"))),
    )

  def conflictVariant(codes: V3ErrorCode.Conflicts*): EndpointOutput.OneOfVariant[Conflict] =
    oneOfVariant(
      statusCode(StatusCode.Conflict).and(
        jsonBody[Conflict].examples(codes.toList.map(mkExampleConflict)),
      ),
    )

  private def mkExampleNotFound(code: V3ErrorCode.NotFounds) =
    mkExample(NotFound(code, code.template, code.mkExampleMap), code)

  private def mkExampleConflict(code: V3ErrorCode.Conflicts) =
    mkExample(Conflict(code, code.template, code.mkExampleMap), code)

  private def mkExample[O <: V3ErrorInfo](example: O, code: V3ErrorCode) =
    Example.of(example).name(code.toString).description(code.description)
}
