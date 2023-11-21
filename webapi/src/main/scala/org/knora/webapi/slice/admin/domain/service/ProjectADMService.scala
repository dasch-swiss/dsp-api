/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import dsp.valueobjects.RestrictedViewSize
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

trait ProjectADMService {
  def findAll: Task[List[ProjectADM]]
  def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def findByShortname(shortname: String): Task[Option[ProjectADM]] =
    ProjectIdentifierADM.ShortnameIdentifier.fromString(shortname).fold(_ => ZIO.none, findByProjectIdentifier)
  def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM]
  def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]]
  def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]]
  def setProjectRestrictedViewSize(project: ProjectADM, size: RestrictedViewSize): Task[Unit]
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
}

final case class ProjectADMServiceLive(
  private val ontologyRepo: OntologyRepo,
  private val projectRepo: KnoraProjectRepo
) extends ProjectADMService {

  override def findAll: Task[List[ProjectADM]] = projectRepo.findAll().flatMap(ZIO.foreachPar(_)(toProjectADM))
  override def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    projectRepo.findById(projectId).flatMap(ZIO.foreach(_)(toProjectADM))

  private def toProjectADM(knoraProject: KnoraProject): Task[ProjectADM] =
    ZIO.attempt(
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
        ontologies = knoraProject.ontologies.map(_.value)
      ).unescape
    )

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
      selfjoin = SelfJoin.from(project.selfjoin),
      ontologies = project.ontologies.map(InternalIri.apply).toList
    )

  override def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectRepo.findAll()
      keywords  = projects.flatMap(_.keywords.map(_.value)).distinct.sorted
    } yield ProjectsKeywordsGetResponseADM(keywords)

  override def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]] =
    for {
      projectMaybe <- projectRepo.findById(id)
      keywordsMaybe = projectMaybe.map(_.keywords.map(_.value))
      result        = keywordsMaybe.map(ProjectKeywordsGetResponseADM(_))
    } yield result

  override def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]] = {
    val projectGraph = ProjectADMService.projectDataNamedGraphV2(project)
    ontologyRepo
      .findByProject(project.id)
      .map(_.map(_.ontologyMetadata.ontologyIri.toInternalIri))
      .map(_ :+ projectGraph)
  }

  override def setProjectRestrictedViewSize(project: ProjectADM, size: RestrictedViewSize): Task[Unit] =
    projectRepo.setProjectRestrictedViewSize(toKnoraProject(project), size)
}

object ProjectADMServiceLive {
  val layer: URLayer[OntologyRepo & KnoraProjectRepo, ProjectADMServiceLive] =
    ZLayer.fromFunction(ProjectADMServiceLive.apply _)
}
