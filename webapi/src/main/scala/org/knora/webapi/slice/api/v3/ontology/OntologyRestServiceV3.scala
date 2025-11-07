package org.knora.webapi.slice.api.v3.ontology
import zio.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.Rdfs
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
  def asOntologyDto(iri: OntologyIri): IO[NotFound, OntologyDto] =
    for {
      onto <- ontologyRepo.findById(iri).orDie.someOrFail(NotFound(iri))
      meta  = onto.ontologyMetadata
    } yield OntologyDto(iri, meta.label.getOrElse(""), meta.comment.map(_.toString).getOrElse(""))

  def asResourceClassDto(resourceClassIri: ResourceClassIri): IO[NotFound, ResourceClassDto] =
    for {
      clazz     <- ontologyRepo.findClassBy(resourceClassIri).orDie.someOrFail(NotFound(resourceClassIri))
      baseClass <- ontologyRepo.findKnoraApiBaseClass(resourceClassIri).orDie
      label      = languageString(clazz, Rdfs.Label)
      comment    = languageString(clazz, Rdfs.Comment)
    } yield ResourceClassDto(resourceClassIri.toComplexSchema.toIri, baseClass.toComplexSchema.toIri, label, comment)

  private def languageString(clazz: ReadClassInfoV2, predicateIri: String): List[LanguageStringDto] =
    clazz.entityInfoContent.predicates.get(predicateIri.toSmartIri).flatMap(LanguageStringDto.from).toList

}

object OntologyRestServiceV3 {
  val layer = ZLayer.derive[OntologyRestServiceV3]
}
