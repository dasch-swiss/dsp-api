/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

trait ProjectADMService {
  def findAll: Task[List[ProjectADM]]
  def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]]
  def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM]
  def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]]
}

final case class ProjectADMServiceLive(
  private val ontologyRepo: OntologyRepo,
  private val projectRepo: KnoraProjectRepo
) extends ProjectADMService {

  override def findAll: Task[List[ProjectADM]] = projectRepo.findAll().flatMap(ZIO.foreach(_)(toProjectADM))
  override def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    projectRepo.findByProjectIdentifier(projectId).flatMap(ZIO.foreach(_)(toProjectADM))

  private def toProjectADM(dspProject: KnoraProject): Task[ProjectADM] =
    for {
      ontologyIris <- ontologyRepo.findByProject(dspProject.id).map(_.map(_.ontologyMetadata.ontologyIri.toIri))
    } yield ProjectADM(
      id = dspProject.id.value,
      shortname = dspProject.shortname,
      shortcode = dspProject.shortcode,
      longname = dspProject.longname,
      description = dspProject.description,
      keywords = dspProject.keywords,
      logo = dspProject.logo,
      status = dspProject.status,
      selfjoin = dspProject.selfjoin,
      ontologies = ontologyIris
    ).unescape

  override def findAllProjectsKeywords: Task[ProjectsKeywordsGetResponseADM] =
    for {
      projects <- projectRepo.findAll()
      keywords  = projects.flatMap(_.keywords).distinct.sorted
    } yield ProjectsKeywordsGetResponseADM(keywords)

  override def findProjectKeywordsBy(id: ProjectIdentifierADM): Task[Option[ProjectKeywordsGetResponseADM]] =
    projectRepo.findByProjectIdentifier(id).map(_.map(_.keywords).map(ProjectKeywordsGetResponseADM))
}

object ProjectADMServiceLive {
  val layer: URLayer[OntologyRepo with KnoraProjectRepo, ProjectADMServiceLive] =
    ZLayer.fromFunction(ProjectADMServiceLive.apply _)
}
