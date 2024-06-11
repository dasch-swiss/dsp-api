/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.ProjectService.projectDataNamedGraphV2
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class ProjectService(
  private val ontologyRepo: OntologyRepo,
  private val knoraProjectService: KnoraProjectService,
) {

  def findAllRegularProjects: Task[Chunk[Project]] = knoraProjectService
    .findAll()
    .map(_.filter(_.id.isRegularProjectIri))
    .flatMap(ZIO.foreach(_)(toProject))

  def findById(id: ProjectIri): Task[Option[Project]] =
    knoraProjectService.findById(id).flatMap(toProject)

  def findByShortcode(shortcode: Shortcode): Task[Option[Project]] =
    knoraProjectService.findByShortcode(shortcode).flatMap(toProject)

  def findByShortname(shortname: Shortname): Task[Option[Project]] =
    knoraProjectService.findByShortname(shortname).flatMap(toProject)

  def findByIds(id: Seq[ProjectIri]): Task[Seq[Project]] = ZIO.foreach(id)(findById).map(_.flatten)

  private def toProject(knoraProject: Option[KnoraProject]): Task[Option[Project]] =
    ZIO.foreach(knoraProject)(toProject)

  private def toProject(knoraProject: KnoraProject): Task[Project] = ontologyRepo
    .findByProject(knoraProject)
    .map(_.map(_.ontologyMetadata.ontologyIri.toIri))
    .map(ontologies =>
      Project(
        knoraProject.id.value,
        knoraProject.shortname.value,
        knoraProject.shortcode.value,
        knoraProject.longname.map(_.value),
        knoraProject.description.map(_.value),
        knoraProject.keywords.map(_.value),
        knoraProject.logo.map(_.value),
        ontologies,
        knoraProject.status.value,
        knoraProject.selfjoin.value,
      ),
    )

  private def toKnoraProject(project: Project, restrictedView: RestrictedView): KnoraProject =
    KnoraProject(
      id = ProjectIri.unsafeFrom(project.id),
      shortname = Shortname.unsafeFrom(project.shortname),
      shortcode = Shortcode.unsafeFrom(project.shortcode),
      longname = project.longname.map(Longname.unsafeFrom),
      description = NonEmptyChunk
        .fromIterable(project.description.head, project.description.tail)
        .map(Description.unsafeFrom),
      keywords = project.keywords.map(Keyword.unsafeFrom).toList,
      logo = project.logo.map(Logo.unsafeFrom),
      status = Status.from(project.status),
      selfjoin = SelfJoin.from(project.selfjoin),
      restrictedView,
    )

  def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]] = {
    val projectGraph = ProjectService.projectDataNamedGraphV2(project)
    ontologyRepo
      .findByProject(project.id)
      .map(_.map(_.ontologyMetadata.ontologyIri.toInternalIri))
      .map(_ :+ projectGraph)
  }

  def setProjectRestrictedView(project: Project, settings: RestrictedView): Task[RestrictedView] =
    knoraProjectService.setProjectRestrictedView(toKnoraProject(project, settings), settings)

  def createProject(createReq: ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest): Task[Project] =
    knoraProjectService.createProject(createReq).flatMap(toProject)

  def updateProject(project: KnoraProject, updateReq: ProjectUpdateRequest): Task[Project] =
    knoraProjectService.updateProject(project, updateReq).flatMap(toProject)

  def erase(project: KnoraProject): Task[Unit] = knoraProjectService.erase(project)
}

object ProjectService {

  /**
   * Given the [[ProjectADM]] constructs the project's data named graph.
   *
   * @param project A [[ProjectADM]].
   * @return the [[InternalIri]] of the project's data named graph.
   */
  def projectDataNamedGraphV2(project: Project): InternalIri = {
    val shortcode = Shortcode.unsafeFrom(project.shortcode)
    val shortname = Shortname.unsafeFrom(project.shortname)
    projectDataNamedGraphV2(shortcode, shortname)
  }

  /**
   * Given the [[KnoraProject]] constructs the project's data named graph.
   *
   * @param project A [[KnoraProject]].
   * @return the [[InternalIri]] of the project's data named graph.
   */
  def projectDataNamedGraphV2(project: KnoraProject): InternalIri =
    projectDataNamedGraphV2(project.shortcode, project.shortname)

  private def projectDataNamedGraphV2(shortcode: Shortcode, shortname: Shortname) =
    InternalIri(s"${OntologyConstants.NamedGraphs.DataNamedGraphStart}/${shortcode.value}/${shortname.value}")

  val layer = ZLayer.derive[ProjectService]
}
