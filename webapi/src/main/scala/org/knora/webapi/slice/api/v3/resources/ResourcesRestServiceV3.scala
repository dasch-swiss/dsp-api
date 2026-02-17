/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources
import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.ResourceClassAndCountDto
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.api.v3.ontology.OntologyRestServiceV3
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo

class ResourcesRestServiceV3(
  projectService: KnoraProjectService,
  ontologyRepo: OntologyRepo,
  ontologyRestService: OntologyRestServiceV3,
  resourcesRepo: ResourcesRepo,
)(implicit val sf: StringFormatter) {

  def resourcesPerOntology(projectIri: ProjectIri): IO[V3ErrorInfo, List[OntologyAndResourceClasses]] =
    for {
      prj         <- projectService.findById(projectIri).orDie.someOrFail(NotFound(projectIri))
      ontologies  <- ontologyRepo.findByProject(projectIri).orDie
      allClasses   = ontologies.flatMap(_.resourceClassIris).toList
      classGroups <- ZIO.foreach(allClasses)(resolveClassGroup).map(_.toMap).orDie
      allIris      = classGroups.values.flatten.toList.distinct
      countsByIri <- resourcesRepo.countByResourceClasses(allIris, prj).orDie
      result      <- ZIO.foreach(ontologies.map(o => o.ontologyIri -> o.resourceClassIris).toList)((ontoIri, classes) =>
                  for {
                    onto <- ontologyRestService.asOntologyDto(ontoIri)
                    rcls <- ZIO.foreach(classes)(iri =>
                              for {
                                rc   <- ontologyRestService.asResourceClassDto(iri)
                                group = classGroups.getOrElse(iri, List(iri))
                                count = group.flatMap(countsByIri.get).sum
                              } yield ResourceClassAndCountDto(rc, count),
                            )
                  } yield OntologyAndResourceClasses(onto, rcls),
                )
    } yield result

  private def resolveClassGroup(iri: ResourceClassIri) = for {
    subclasses  <- ontologyRepo.findAllSubclassesBy(iri)
    ontoIri      = iri.ontologyIri.smartIri.toInternalSchema
    sameOntoSubs = subclasses
                     .filter(_.entityInfoContent.classIri.getOntologyFromEntity.toInternalSchema == ontoIri)
                     .map(sc => ResourceClassIri.unsafeFrom(sc.entityInfoContent.classIri))
  } yield iri -> (iri :: sameOntoSubs)
}

object ResourcesRestServiceV3 {
  val layer = ZLayer.derive[ResourcesRestServiceV3]
}
