/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.cache.api.CacheService

final case class ProjectADMService(
  private val ontologyRepo: OntologyRepo,
  private val projectRepo: KnoraProjectRepo,
  private val cacheService: CacheService
) {

  def findAll: Task[List[ProjectADM]] = projectRepo.findAll().flatMap(ZIO.foreachPar(_)(toProjectADM))

  def findById(id: ProjectIri): Task[Option[ProjectADM]] =
    findByProjectIdentifier(ProjectIdentifierADM.from(id))

  def findByIds(id: Seq[ProjectIri]): Task[Seq[ProjectADM]] = ZIO.foreach(id)(findById).map(_.flatten)

  def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    cacheService.getProjectADM(projectId).flatMap {
      case Some(project) => ZIO.some(project)
      case None =>
        projectRepo.findById(projectId).flatMap(ZIO.foreach(_)(toProjectADM)).tap {
          case Some(prj) => cacheService.putProjectADM(prj)
          case None      => ZIO.unit
        }
    }

  private def toProjectADM(knoraProject: KnoraProject): Task[ProjectADM] = for {
    ontologies <- ontologyRepo.findByProject(knoraProject).map(_.map(_.ontologyMetadata.ontologyIri.toIri))
    prj <- ZIO.attempt(
             ProjectADM(
               id = knoraProject.id.value,
               shortname = knoraProject.shortname.value,
               shortcode = knoraProject.shortcode.value,
               longname = knoraProject.longname.map(_.value),
               description = knoraProject.description.map(_.value),
               keywords = knoraProject.keywords.map(_.value),
               logo = knoraProject.logo.map(_.value),
               status = knoraProject.status.value,
               selfjoin = knoraProject.selfjoin.value,
               ontologies = ontologies
             ).unescape
           )
  } yield prj

  private def toKnoraProject(project: ProjectADM): KnoraProject =
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
      selfjoin = SelfJoin.from(project.selfjoin)
    )

  def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectRepo.findAll()
      keywords  = projects.flatMap(_.keywords.map(_.value)).distinct.sorted
    } yield ProjectsKeywordsGetResponseADM(keywords)

  def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]] =
    for {
      projectMaybe <- projectRepo.findById(id)
      keywordsMaybe = projectMaybe.map(_.keywords.map(_.value))
      result        = keywordsMaybe.map(ProjectKeywordsGetResponseADM(_))
    } yield result

  def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]] = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    ontologyRepo
      .findByProject(project.id)
      .map(_.map(_.ontologyMetadata.ontologyIri.toInternalIri))
      .map(_ :+ projectGraph)
  }

  def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[RestrictedView] = {
    val newSettings = settings match {
      case RestrictedView.Watermark(false) => RestrictedView.default
      case s                               => s
    }
    projectRepo.setProjectRestrictedView(project, newSettings).as(newSettings)
  }

  def setProjectRestrictedView(project: ProjectADM, settings: RestrictedView): Task[RestrictedView] =
    setProjectRestrictedView(toKnoraProject(project), settings)
}

object ProjectADMService {

  /**
   * Given the [[ProjectADM]] constructs the project's data named graph.
   *
   * @param project A [[ProjectADM]].
   * @return the [[InternalIri]] of the project's data named graph.
   */
  def projectDataNamedGraphV2(project: ProjectADM): InternalIri = {
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

  val layer = ZLayer.derive[ProjectADMService]
}
