/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.cache.CacheService

final case class ProjectService(
  private val ontologyRepo: OntologyRepo,
  private val knoraProjectService: KnoraProjectService,
  private val cacheService: CacheService,
) {

  def findAllRegularProjects: Task[Chunk[Project]] = knoraProjectService
    .findAll()
    .map(_.filter(_.id.isRegularProjectIri))
    .flatMap(ZIO.foreach(_)(toProject))

  def findById(id: ProjectIri): Task[Option[Project]] =
    findByProjectIdentifier(ProjectIdentifierADM.from(id))

  def findByShortcode(shortcode: Shortcode): Task[Option[Project]] =
    findByProjectIdentifier(ShortcodeIdentifier.from(shortcode))

  def findByShortname(shortname: Shortname): Task[Option[Project]] =
    findByProjectIdentifier(ShortnameIdentifier.from(shortname))

  def findByIds(id: Seq[ProjectIri]): Task[Seq[Project]] = ZIO.foreach(id)(findById).map(_.flatten)

  private def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[Project]] =
    cacheService.getProjectADM(projectId).flatMap {
      case Some(project) => ZIO.some(project)
      case None =>
        knoraProjectService.findById(projectId).flatMap(ZIO.foreach(_)(toProject)).tap {
          case Some(prj) => cacheService.putProjectADM(prj)
          case None      => ZIO.unit
        }
    }

  private def toProject(knoraProject: KnoraProject): Task[Project] = for {
    ontologies <- ontologyRepo.findByProject(knoraProject).map(_.map(_.ontologyMetadata.ontologyIri.toIri))
    prj <- ZIO.attempt(
             Project(
               id = knoraProject.id.value,
               shortname = knoraProject.shortname.value,
               shortcode = knoraProject.shortcode.value,
               longname = knoraProject.longname.map(_.value),
               description = knoraProject.description.map(_.value),
               keywords = knoraProject.keywords.map(_.value),
               logo = knoraProject.logo.map(_.value),
               status = knoraProject.status.value,
               selfjoin = knoraProject.selfjoin.value,
               ontologies = ontologies,
             ).unescape,
           )
  } yield prj

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

  def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponse]] =
    for {
      projectMaybe <- knoraProjectService.findById(id)
      keywordsMaybe = projectMaybe.map(_.keywords.map(_.value))
      result        = keywordsMaybe.map(ProjectKeywordsGetResponse.apply)
    } yield result

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
