package org.knora.webapi.slice.admin.domain.service
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.Task
import zio.ZIO

final case class ProjectEraseService(
  projectService: ProjectService,
  ontologyCache: OntologyCache,
  iriConverter: IriConverter,
  triplestore: TriplestoreService,
  ingestClient: DspIngestClient,
) {

  def eraseProject(knoraProject: KnoraProject): Task[Unit] = for {
    graphsToDelete <- projectService.getNamedGraphsForProject(knoraProject).map(_.map(_.value))
    _              <- ZIO.foreachDiscard(graphsToDelete)(triplestore.dropGraph)
    _              <- ontologyCache.loadOntologies(KnoraSystemInstances.Users.SystemUser)
    _              <- projectService.erase(knoraProject)
    _              <- ingestClient.eraseProject(knoraProject.shortcode)
    // TODO: remove groups/memberships/doap/ap/lists

  } yield ()

}
