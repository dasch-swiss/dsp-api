/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import sttp.tapir.Schema
import zio.json.*

import java.time.Instant

import org.knora.webapi.slice.api.v3.LanguageStringDto

final case class AddClassMappingsRequest(mappings: List[String])
object AddClassMappingsRequest {
  given JsonCodec[AddClassMappingsRequest] = DeriveJsonCodec.gen[AddClassMappingsRequest]
  given Schema[AddClassMappingsRequest]    = Schema.derived[AddClassMappingsRequest]
}

final case class AddPropertyMappingsRequest(mappings: List[String])
object AddPropertyMappingsRequest {
  given JsonCodec[AddPropertyMappingsRequest] = DeriveJsonCodec.gen[AddPropertyMappingsRequest]
  given Schema[AddPropertyMappingsRequest]    = Schema.derived[AddPropertyMappingsRequest]
}

/**
 * V3 response for PUT/DELETE class mapping endpoints.
 *
 * `subClassOf` lists all current super-class IRIs in API v2 complex schema form; order is unspecified.
 * `lastModificationDate` is always updated on write, even for no-op operations.
 */
final case class ClassMappingResponse(
  classIri: String,
  ontologyIri: String,
  subClassOf: List[String],
  label: List[LanguageStringDto],
  comment: List[LanguageStringDto],
  lastModificationDate: Instant,
)
object ClassMappingResponse {
  given JsonCodec[ClassMappingResponse] = DeriveJsonCodec.gen[ClassMappingResponse]
  given Schema[ClassMappingResponse]    = Schema.derived[ClassMappingResponse]
}

/**
 * V3 response for PUT/DELETE property mapping endpoints.
 *
 * `subPropertyOf` lists all current super-property IRIs in API v2 complex schema form; order is unspecified.
 * `lastModificationDate` is always updated on write, even for no-op operations.
 */
final case class PropertyMappingResponse(
  propertyIri: String,
  ontologyIri: String,
  subPropertyOf: List[String],
  label: List[LanguageStringDto],
  comment: List[LanguageStringDto],
  lastModificationDate: Instant,
)
object PropertyMappingResponse {
  given JsonCodec[PropertyMappingResponse] = DeriveJsonCodec.gen[PropertyMappingResponse]
  given Schema[PropertyMappingResponse]    = Schema.derived[PropertyMappingResponse]
}
