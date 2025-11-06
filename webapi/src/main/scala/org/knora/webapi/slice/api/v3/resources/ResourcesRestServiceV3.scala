package org.knora.webapi.slice.api.v3.resources
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.LanguageStringDto
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.OntologyDto
import org.knora.webapi.slice.api.v3.ResourceClassAndCountDto
import org.knora.webapi.slice.api.v3.ResourceClassDto
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo
import zio.*

class ResourcesRestServiceV3(
  private val projectService: KnoraProjectService,
  private val ontologyRepo: OntologyRepo,
  private val resourcesRepo: ResourcesRepo,
)(implicit val sf: StringFormatter) {

  def resourcesPerOntology(
    user: User,
  )(projectIri: ProjectIri): IO[V3ErrorInfo, List[OntologyAndResourceClasses]] =
    for {
      prj <- projectService.findById(projectIri).orDie.someOrFail(NotFound(projectIri))
      // ignore the user for now to please the compiler
      _           = user
      ontologies <- ontologyRepo.findByProject(projectIri).orDie
      result <- ZIO
                  .foreach(ontologies.map(o => o.ontologyIri -> o.resourceClassIris).toList)((ontoIri, classes) =>
                    for {
                      onto <- asOntologyDto(ontoIri)
                      rcls <- ZIO.foreach(classes)(asResourceClassAndCount(prj))
                    } yield OntologyAndResourceClasses(onto, rcls),
                  )
    } yield result

  private def asOntologyDto(iri: OntologyIri): IO[NotFound, OntologyDto] =
    for {
      onto <- ontologyRepo.findById(iri).orDie.someOrFail(NotFound(iri))
      meta  = onto.ontologyMetadata
    } yield OntologyDto(iri, meta.label.getOrElse(""), meta.comment.map(_.toString).getOrElse(""))

  private def asResourceClassAndCount(prj: KnoraProject)(iri: ResourceClassIri) = for {
    resourceClass <- asResourceClassDto(iri)
    count         <- resourcesRepo.countByResourceClass(iri, prj).orDie
  } yield ResourceClassAndCountDto(resourceClass, count)

  private def asResourceClassDto(resourceClassIri: ResourceClassIri): IO[NotFound, ResourceClassDto] =
    for {
      clazz     <- ontologyRepo.findClassBy(resourceClassIri).orDie.someOrFail(NotFound(resourceClassIri))
      baseClass <- ontologyRepo.findKnoraApiBaseClass(resourceClassIri).orDie
      label      = languageString(clazz, Rdfs.Label)
      comment    = languageString(clazz, Rdfs.Comment)
    } yield ResourceClassDto(resourceClassIri.toComplexSchema.toIri, baseClass.toComplexSchema.toIri, label, comment)

  private def languageString(clazz: ReadClassInfoV2, predicateIri: String): List[LanguageStringDto] =
    clazz.entityInfoContent.predicates.get(predicateIri.toSmartIri).flatMap(asLanguageStrings).toList

  private def asLanguageStrings = (info: PredicateInfoV2) =>
    info.objects.collect { case str: StringLiteralV2 => str }.map(asLanguageString).toList

  private def asLanguageString = (str: StringLiteralV2) =>
    LanguageStringDto(str.value, str.languageCode.getOrElse(LanguageCode.Default))

}

object ResourcesRestServiceV3 {
  val layer = ZLayer.derive[ResourcesRestServiceV3]
}
