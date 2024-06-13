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
import org.knora.webapi.slice.common.Value.StringValue
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

  private def logPrefix(project: KnoraProject): String   = s"ERASE Project ${project.shortcode.value}:"
  private def mkString(values: Seq[StringValue]): String = s"'${values.map(_.value).mkString(",")}'"

  def eraseProject(project: KnoraProject): Task[Unit] = for {
    groupsToDelete <- groupService.findByProject(project)
    _              <- cleanUpUsersAndGroups(project, groupsToDelete)
    _              <- cleanUpPermissions(project)
    _              <- removeOntologyAndDataGraphs(project)
    _              <- projectService.erase(project)
    _              <- ingestClient.eraseProject(project.shortcode).logError.ignore
  } yield ()

  private def cleanUpUsersAndGroups(project: KnoraProject, groups: Chunk[KnoraGroup]): UIO[Unit] = for {
    _ <- ZIO.logInfo(s"${logPrefix(project)} Cleaning up memberships and groups ${mkString(groups.map(_.id))}")
    _ <- removeUserProjectAdminMemberships(project).logError.ignore
    _ <- removeUserProjectMemberShips(project).logError.ignore
    _ <- removeUserGroupMemberShips(groups, project).logError.ignore
    _ <- groupService.deleteAll(groups).orDie
  } yield ()

  private def removeUserProjectAdminMemberships(project: KnoraProject) =
    userService
      .findByProjectAdminMembership(project)
      .tap(u =>
        ZIO.logInfo(s"${logPrefix(project)} Removing project admins: ${mkString(u.map(_.id))}").unless(u.isEmpty),
      )
      .flatMap(userService.removeUsersFromProjectAsAdmin(_, project))

  private def removeUserProjectMemberShips(project: KnoraProject) =
    userService
      .findByProjectMembership(project)
      .tap(u =>
        ZIO.logInfo(s"${logPrefix(project)} Removing project members: ${mkString(u.map(_.id))}").unless(u.isEmpty),
      )
      .flatMap(userService.removeUsersFromProject(_, project))

  private def removeUserGroupMemberShips(groupsToRemove: Chunk[KnoraGroup], project: KnoraProject) =
    ZIO.foreachDiscard(groupsToRemove)(group =>
      userService
        .findByGroupMembership(group)
        .tap(u =>
          ZIO.logInfo(s"${logPrefix(project)} Removing users from group ${group.id.value}: ${mkString(u.map(_.id))}"),
        )
        .flatMap(userService.removeUsersFromKnoraGroup(_, group)),
    )

  private def cleanUpPermissions(project: KnoraProject) = for {
    ap   <- apRepo.findByProject(project)
    doap <- doapRepo.findByProject(project)
    _ <- ZIO.logInfo(
           s"${logPrefix(project)} Removing permissions ap ${mkString(ap.map(_.id))} , doap ${mkString(doap.map(_.id))}",
         )
    _ <- apRepo.deleteAll(ap)
    _ <- doapRepo.deleteAll(doap)
  } yield ()

  private def removeOntologyAndDataGraphs(project: KnoraProject) = for {
    graphsToDelete <- projectService.getNamedGraphsForProject(project)
    _              <- ZIO.logInfo(s"${logPrefix(project)} Removing graphs ${graphsToDelete.map(_.value)}")
    _              <- ZIO.foreachDiscard(graphsToDelete)(triplestore.dropGraphByIri)
    _              <- ontologyCache.loadOntologies()
  } yield ()
}

object ProjectEraseService {
  val layer = ZLayer.derive[ProjectEraseService]
}
