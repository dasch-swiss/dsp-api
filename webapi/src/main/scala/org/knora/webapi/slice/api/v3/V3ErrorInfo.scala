/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import zio.Chunk
import zio.json.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri

sealed trait V3ErrorInfo {
  def message: String
  def errors: Chunk[ErrorDetail]
}

final case class NotFound private (message: String, errors: Chunk[ErrorDetail]) extends V3ErrorInfo
object NotFound:
  given JsonCodec[NotFound] = DeriveJsonCodec.gen[NotFound]

  def apply(code: V3ErrorCode.NotFounds, message: String, details: Map[String, String]): NotFound =
    NotFound(message, Chunk(ErrorDetail(code, message, details)))

  def apply(resourceClassIri: ResourceClassIri): NotFound =
    apply(
      V3ErrorCode.resourceClass_not_found,
      s"The resource class with IRI $resourceClassIri was not found.",
      Map.empty,
    )

  def apply(ontologyIri: OntologyIri): NotFound =
    apply(V3ErrorCode.ontology_not_found, s"The ontology with IRI $ontologyIri was not found.", Map.empty)

  def apply(projectIri: ProjectIri): NotFound =
    apply(V3ErrorCode.project_not_found, s"The project with IRI $projectIri was not found.", Map.empty)

  def from(resourceIri: ResourceIri): NotFound =
    apply(
      V3ErrorCode.resource_not_found,
      s"The resource with IRI '$resourceIri' was not found.",
      Map("resourceIri" -> resourceIri.toString),
    )

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
  given badRequestEncoder: JsonCodec[BadRequest]     = DeriveJsonCodec.gen[BadRequest]
  given unauthorizedEncoder: JsonCodec[Unauthorized] = DeriveJsonCodec.gen[Unauthorized]
  given forbiddenEncoder: JsonCodec[Forbidden]       = DeriveJsonCodec.gen[Forbidden]
}
