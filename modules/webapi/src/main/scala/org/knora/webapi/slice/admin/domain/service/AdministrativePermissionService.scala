/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.DuplicateValueException
import dsp.errors.UpdateNotPerformedException
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class AdministrativePermissionService(repo: AdministrativePermissionRepo, triplestore: TriplestoreService) {

  def findById(permissionIri: PermissionIri): Task[Option[AdministrativePermission]] =
    repo.findById(permissionIri)

  def findByProject(projectIri: ProjectIri): Task[Chunk[AdministrativePermission]] =
    repo.findByProject(projectIri)

  def findByGroupAndProject(groupIri: GroupIri, projectIri: ProjectIri): Task[Option[AdministrativePermission]] =
    repo.findByGroupAndProject(groupIri, projectIri)

  def create(
    project: KnoraProject,
    group: KnoraGroup,
    permissions: Chunk[AdministrativePermissionPart],
  ): Task[AdministrativePermission] =
    create(AdministrativePermission(PermissionIri.makeNew(project.shortcode), group.id, project.id, permissions))

  def create(adminPermission: AdministrativePermission): Task[AdministrativePermission] =
    findByGroupAndProject(adminPermission.forGroup, adminPermission.forProject).filterOrFail(_.isEmpty)(
      DuplicateValueException(
        s"An administrative permission for project: '${adminPermission.forProject}' and " +
          s"group: '${adminPermission.forGroup}' combination already exists.",
      ),
    ) *> repo.save(adminPermission)

  def delete(entity: AdministrativePermission): Task[Unit] = for {
    _ <- ZIO
           .fail(UpdateNotPerformedException(s"Permission ${entity.id} is in use and cannot be deleted."))
           .whenZIO(triplestore.isIriInObjectPosition(Rdf.iri(entity.id.value)))
    _ <- repo.delete(entity)
  } yield ()

  def setForGroup(entity: AdministrativePermission, newGroupIri: GroupIri): Task[AdministrativePermission] =
    repo.save(entity.copy(forGroup = newGroupIri))

  def setParts(
    entity: AdministrativePermission,
    newParts: Chunk[AdministrativePermissionPart],
  ): Task[AdministrativePermission] =
    repo.save(entity.copy(permissions = newParts))
}

object AdministrativePermissionService {
  val layer = ZLayer.derive[AdministrativePermissionService]
}
