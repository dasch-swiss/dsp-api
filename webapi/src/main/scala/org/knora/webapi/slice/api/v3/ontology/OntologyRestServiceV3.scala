/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology
import zio.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.slice.api.v3.LanguageStringDto
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.OntologyDto
import org.knora.webapi.slice.api.v3.ResourceClassDto
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

class OntologyRestServiceV3(ontologyRepo: OntologyRepo)(implicit val sf: StringFormatter) {

  def asOntologyDto(iri: OntologyIri): IO[NotFound, OntologyDto] = for {
    onto <- ontologyRepo.findById(iri).orDie.someOrFail(NotFound(iri))
    meta  = onto.ontologyMetadata
  } yield OntologyDto(iri, meta.label.getOrElse(""), meta.comment.map(_.toString).getOrElse(""))

  def asResourceClassDto(classIri: ResourceClassIri): IO[NotFound, ResourceClassDto] = for {
    readClassInfo  <- ontologyRepo.findClassBy(classIri).orDie.someOrFail(NotFound(classIri))
    representation <- ontologyRepo.knoraApiRepresentationClassIriFor(classIri).orDie
    label           = languageString(readClassInfo, Rdfs.Label.toSmartIri)
    comment         = languageString(readClassInfo, Rdfs.Comment.toSmartIri)
  } yield ResourceClassDto(classIri.toComplexSchema.toIri, representation.toComplexSchema.toIri, label, comment)

  private def languageString(clazz: ReadClassInfoV2, predicateIri: SmartIri): List[LanguageStringDto] =
    clazz.entityInfoContent.predicates.get(predicateIri).flatMap(LanguageStringDto.from).toList
}

object OntologyRestServiceV3 {
  val layer = ZLayer.derive[OntologyRestServiceV3]
}
