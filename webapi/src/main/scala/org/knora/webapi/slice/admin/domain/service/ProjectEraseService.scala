/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ProjectEraseService(
  private val apRepo: AdministrativePermissionRepo,
  private val doapRepo: DefaultObjectAccessPermissionRepo,
  private val groupService: KnoraGroupService,
  private val ingestClient: DspIngestClient,
  private val ontologyCache: OntologyCache,
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val userService: KnoraUserService,
) {
  private def logPrefix(project: KnoraProject): String = logPrefix(project.shortcode)
  private def logPrefix(shortcode: Shortcode): String  = s"ERASE - $shortcode:"

  def eraseProject(project: KnoraProject): Task[Unit] = for {
    groupsToDelete <- groupService.findByProject(project)
    _              <- cleanUpUsersAndGroups(project, groupsToDelete)
    _              <- cleanUpPermissions(project)
    _              <- removeOntologyAndDataGraphs(project)
    _              <- projectService.erase(project)
    _              <- ingestClient.eraseProject(project.shortcode).logError.ignore
  } yield ()

  private def cleanUpUsersAndGroups(project: KnoraProject, groups: Chunk[KnoraGroup]): UIO[Unit] = for {
    _ <- ZIO.logInfo(s"${logPrefix(project)} cleaning up groups ${groups.map(_.id)}")
    _ <- removeUserProjectAdminMemberships(project).logError.ignore
    _ <- removeUserProjectMemberShips(project).logError.ignore
    _ <- removeUserGroupMemberShips(groups).logError.ignore
    _ <- groupService.deleteAll(groups).orDie
  } yield ()

  private def removeUserProjectAdminMemberships(project: KnoraProject) =
    userService.findByProjectAdminMembership(project).flatMap(userService.removeUsersFromProjectAsAdmin(_, project))

  private def removeUserProjectMemberShips(project: KnoraProject) =
    userService.findByProjectMembership(project).flatMap(userService.removeUsersFromProject(_, project))

  private def removeUserGroupMemberShips(groupsToRemove: Chunk[KnoraGroup]) =
    ZIO.foreachDiscard(groupsToRemove)(group =>
      userService.findByGroupMembership(group).flatMap(userService.removeUsersFromKnoraGroup(_, group)),
    )

  private def cleanUpPermissions(project: KnoraProject) = for {
    ap   <- apRepo.findByProject(project)
    doap <- doapRepo.findByProject(project)
    _    <- ZIO.logInfo(s"${logPrefix(project)} cleaning permissions ap ${ap.map(_.id)} , doap ${doap.map(_.id)}")
    _    <- apRepo.deleteAll(ap)
    _    <- doapRepo.deleteAll(doap)
  } yield ()

  private def removeOntologyAndDataGraphs(project: KnoraProject) = for {
    graphsToDelete <- projectService.getNamedGraphsForProject(project)
    _              <- ZIO.logInfo(s"${logPrefix(project)} removing graphs ${graphsToDelete.map(_.value)}")
    _              <- ZIO.foreachDiscard(graphsToDelete)(triplestore.dropGraphByIri)
    _              <- ontologyCache.loadOntologies()
  } yield ()
}

object ProjectEraseService {
  val layer = ZLayer.derive[ProjectEraseService]
}
