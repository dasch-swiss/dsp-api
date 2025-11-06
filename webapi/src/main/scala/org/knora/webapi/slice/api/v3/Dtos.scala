/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import sttp.tapir.Schema
import zio.*
import zio.json.*
import org.knora.webapi.slice.common.domain.LanguageCode

final case class LanguageStringDto(value: String, language: LanguageCode)
object LanguageStringDto {
  given JsonCodec[LanguageStringDto] = DeriveJsonCodec.gen[LanguageStringDto]
  given Schema[LanguageStringDto]    = Schema.derived[LanguageStringDto]
}

final case class ResourceClassDto(
  iri: String,
  label: List[LanguageStringDto],
  comment: List[LanguageStringDto],
)
object ResourceClassDto {
  def apply(iri: ResourceClassIri, label: List[LanguageStringDto], comment: List[LanguageStringDto]): ResourceClassDto =
    apply(iri.smartIri, label, comment)
  def apply(iri: SmartIri, label: List[LanguageStringDto], comment: List[LanguageStringDto]): ResourceClassDto =
    ResourceClassDto(iri.toComplexSchema.toString, label, comment)
  given JsonCodec[ResourceClassDto] = DeriveJsonCodec.gen[ResourceClassDto]
  given Schema[ResourceClassDto]    = Schema.derived[ResourceClassDto]
}

final case class OntologyDto(iri: String, label: String, comment: String)
object OntologyDto {
  def apply(iri: OntologyIri, label: String, comment: String): OntologyDto = apply(iri.smartIri, label, comment)
  def apply(iri: SmartIri, label: String, comment: String): OntologyDto =
    OntologyDto(iri.toComplexSchema.toString, label, comment)
  given JsonCodec[OntologyDto] = DeriveJsonCodec.gen[OntologyDto]
  given Schema[OntologyDto]    = Schema.derived[OntologyDto]
}

final case class OntologyAndResourceClasses(
  ontology: OntologyDto,
  resourceClasses: List[ResourceClassDto],
  itemCount: Int,
)
object OntologyAndResourceClasses {
  given JsonCodec[OntologyAndResourceClasses] = DeriveJsonCodec.gen[OntologyAndResourceClasses]
  given Schema[OntologyAndResourceClasses]    = Schema.derived[OntologyAndResourceClasses]
}
