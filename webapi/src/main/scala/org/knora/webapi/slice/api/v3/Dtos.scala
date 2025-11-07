/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.Schema
import zio.*
import zio.json.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode

final case class LanguageStringDto(value: String, language: LanguageCode)
object LanguageStringDto {
  given JsonCodec[LanguageStringDto] = DeriveJsonCodec.gen[LanguageStringDto]
  given Schema[LanguageStringDto]    = Schema.derived[LanguageStringDto]

  def apply(literal: StringLiteralV2): LanguageStringDto =
    LanguageStringDto(literal.value, literal.languageCode.getOrElse(LanguageCode.Default))

  def from(info: PredicateInfoV2): List[LanguageStringDto] =
    info.objects.collect { case str: StringLiteralV2 => str }.map(LanguageStringDto.apply).toList
}

final case class ResourceClassDto(
  iri: String,
  baseClassIri: String,
  label: List[LanguageStringDto],
  comment: List[LanguageStringDto],
)
object ResourceClassDto {
  def apply(
    iri: ResourceClassIri,
    baseClass: ResourceClassIri,
    label: List[LanguageStringDto],
    comment: List[LanguageStringDto],
  ): ResourceClassDto =
    apply(iri.smartIri, baseClass.smartIri, label, comment)
  def apply(
    iri: SmartIri,
    baseClass: SmartIri,
    label: List[LanguageStringDto],
    comment: List[LanguageStringDto],
  ): ResourceClassDto =
    ResourceClassDto(iri.toComplexSchema.toString, baseClass.toComplexSchema.toIri, label, comment)
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
  classesAndCount: List[ResourceClassAndCountDto],
)
object OntologyAndResourceClasses {
  given JsonCodec[OntologyAndResourceClasses] = DeriveJsonCodec.gen[OntologyAndResourceClasses]
  given Schema[OntologyAndResourceClasses]    = Schema.derived[OntologyAndResourceClasses]
}

final case class ResourceClassAndCountDto(
  resourceClass: ResourceClassDto,
  itemCount: Int,
)
object ResourceClassAndCountDto {
  given JsonCodec[ResourceClassAndCountDto] = DeriveJsonCodec.gen[ResourceClassAndCountDto]
  given Schema[ResourceClassAndCountDto]    = Schema.derived[ResourceClassAndCountDto]
}
