/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources
import zio.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.v3.BadRequest
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.OntologyAndResourceClasses
import org.knora.webapi.slice.api.v3.ResourceClassAndCountDto
import org.knora.webapi.slice.api.v3.ResourceResponseDto
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.api.v3.ontology.OntologyRestServiceV3
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.api.model.IriDto
import org.knora.webapi.slice.resources.repo.service.ResourcesRepo

class ResourcesRestServiceV3(
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo,
  ontologyRestService: OntologyRestServiceV3,
  projectService: KnoraProjectService,
  resourcesRepo: ResourcesRepo,
)(implicit val sf: StringFormatter) {

  def resourcesPerOntology(projectIri: ProjectIri): IO[V3ErrorInfo, List[OntologyAndResourceClasses]] =
    for {
      prj        <- findProject(projectIri)
      ontologies <- ontologyRepo.findByProject(projectIri).orDie
      result <- ZIO
                  .foreach(ontologies.map(o => o.ontologyIri -> o.resourceClassIris).toList)((ontoIri, classes) =>
                    for {
                      onto <- ontologyRestService.asOntologyDto(ontoIri)
                      rcls <- ZIO.foreachPar(classes)(asResourceClassAndCount(prj)).withParallelism(5)
                    } yield OntologyAndResourceClasses(onto, rcls),
                  )
    } yield result

  private def asResourceClassAndCount(prj: KnoraProject)(iri: ResourceClassIri) = for {
    resourceClass <- ontologyRestService.asResourceClassDto(iri)
    count         <- resourcesRepo.countByResourceClass(iri, prj).orDie
  } yield ResourceClassAndCountDto(resourceClass, count)

  private def findProject(iri: ProjectIri): IO[V3ErrorInfo, KnoraProject] =
    projectService.findById(iri).orDie.someOrFail(NotFound(iri))

  def getResources(user: User)(
    projectIri: ProjectIri,
    resourceClassIri: IriDto,
    pageAndSize: PageAndSize,
  ): IO[V3ErrorInfo, PagedResponse[ResourceResponseDto]] = for {
    prj <- findProject(projectIri)
    resourceClassIri <- iriConverter
                          .asResourceClassIriApiV2Complex(resourceClassIri.value)
                          .mapError(BadRequest.invalidResourceClassIri(resourceClassIri, _))
    page <- resourcesRepo.findResourcesByResourceClassIri(resourceClassIri, prj, user, pageAndSize).orDie
  } yield page.mapData(item => ResourceResponseDto.from(item.iri, item.label))
}

object ResourcesRestServiceV3 {
  val layer = ZLayer.derive[ResourcesRestServiceV3]
}
