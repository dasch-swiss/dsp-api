package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ProjectEraseService(
  projectService: ProjectService,
  ontologyCache: OntologyCache,
  iriConverter: IriConverter,
  triplestore: TriplestoreService,
  ingestClient: DspIngestClient,
  groupService: KnoraGroupService,
  userService: KnoraUserService,
  adminPermissionRepo: AdministrativePermissionRepo,
  defaultObjectAccessPermissionRepo: DefaultObjectAccessPermissionRepo,
) {

  def eraseProject(project: KnoraProject): Task[Unit] = for {

    // cleanup users and groups
    groupsToDelete <- groupService.findByProject(project)
    _              <- removeUserProjectAdminMemberships(project)
    _              <- removeUserProjectMemberShips(project)
    _              <- removeUserGroupMemberShips(groupsToDelete)
    _              <- groupService.deleteAll(groupsToDelete)

    // cleanup permissions
    _ <- adminPermissionRepo.findByProject(project).flatMap(adminPermissionRepo.deleteAll)
    _ <- defaultObjectAccessPermissionRepo.findByProject(project).flatMap(defaultObjectAccessPermissionRepo.deleteAll)

    // remove ontology and data graphs
    graphsToDelete <- projectService.getNamedGraphsForProject(project)
    _              <- ZIO.foreachDiscard(graphsToDelete)(triplestore.dropGraphByIri)
    _              <- ontologyCache.loadOntologies()

    // remove knora project and project in ingest
    _ <- projectService.erase(project)
    _ <- ingestClient.eraseProject(project.shortcode).logError.ignore

  } yield ()

  private def removeUserProjectAdminMemberships(project: KnoraProject) =
    userService.findByProjectAdminMembership(project).flatMap(userService.removeUsersFromProjectAsAdmin(_, project))

  private def removeUserProjectMemberShips(project: KnoraProject) =
    userService.findByProjectMembership(project).flatMap(userService.removeUsersFromProject(_, project))

  private def removeUserGroupMemberShips(groupsToRemove: Chunk[KnoraGroup]) =
    ZIO.foreachDiscard(groupsToRemove)(group =>
      userService.findByGroupMembership(group).flatMap(userService.removeUsersFromGroup(_, group)),
    )
}

object ProjectEraseService {
  val layer = ZLayer.derive[ProjectEraseService]
}
