/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import zio.Chunk
import zio.json.*

import org.knora.webapi.slice.common.KnoraIris.ResourceIri

sealed trait V3ErrorInfo {
  def message: String
  def errors: Chunk[ErrorDetail]
}

case class NotFound(message: String = "Not Found", errors: Chunk[ErrorDetail] = Chunk.empty) extends V3ErrorInfo
object NotFound {

  private def singleError(code: V3ErrorCode, message: String, details: Map[String, String] = Map.empty): NotFound =
    NotFound(message, Chunk(ErrorDetail(code, message, details)))

  def from(resourceIri: ResourceIri): NotFound =
    singleError(
      V3ErrorCode.resource_not_found,
      s"The resource with IRI '$resourceIri' was not found.",
      Map("resourceIri" -> resourceIri.toString),
    )
}

case class BadRequest(message: String = "Bad Request", errors: Chunk[ErrorDetail] = Chunk.empty) extends V3ErrorInfo

final case class Unauthorized(message: String = "Unauthorized", errors: Chunk[ErrorDetail] = Chunk.empty)
    extends V3ErrorInfo
final case class Forbidden(message: String = "Forbidden", errors: Chunk[ErrorDetail] = Chunk.empty) extends V3ErrorInfo

final case class ErrorDetail(
  code: V3ErrorCode,
  message: String,
  details: Map[String, String] = Map.empty,
)
object ErrorDetail {
  given errorDetailEncoder: JsonCodec[ErrorDetail] = DeriveJsonCodec.gen[ErrorDetail]
}

object V3ErrorInfo {
  given notFoundEncoder: JsonCodec[NotFound]         = DeriveJsonCodec.gen[NotFound]
  given badRequestEncoder: JsonCodec[BadRequest]     = DeriveJsonCodec.gen[BadRequest]
  given unauthorizedEncoder: JsonCodec[Unauthorized] = DeriveJsonCodec.gen[Unauthorized]
  given forbiddenEncoder: JsonCodec[Forbidden]       = DeriveJsonCodec.gen[Forbidden]
}
