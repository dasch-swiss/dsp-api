package org.knora.webapi.slice.admin.domain.service

import zio._

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.DspProject
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

trait ProjectADMService {
  def findAll: Task[List[ProjectADM]]
  def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]]
}

final case class ProjectADMServiceLive(private val ontologyRepo: OntologyRepo, private val projectRepo: DspProjectRepo)
    extends ProjectADMService {

  override def findAll: Task[List[ProjectADM]] = projectRepo.findAll().flatMap(ZIO.foreach(_)(toProjectADM))
  override def findByProjectIdentifier(projectId: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    projectRepo.findByProjectIdentifier(projectId).flatMap(ZIO.foreach(_)(toProjectADM))

  private def toProjectADM(dspProject: DspProject): Task[ProjectADM] =
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
}

object ProjectADMServiceLive {
  val layer: URLayer[OntologyRepo with DspProjectRepo, ProjectADMServiceLive] =
    ZLayer.fromFunction(ProjectADMServiceLive.apply _)
}
