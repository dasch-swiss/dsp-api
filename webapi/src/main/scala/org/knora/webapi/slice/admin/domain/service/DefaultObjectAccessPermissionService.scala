/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Chunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.UpdateNotPerformedException
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class DefaultObjectAccessPermissionService(
  repo: DefaultObjectAccessPermissionRepo,
  triplestore: TriplestoreService,
) {

  def findById(permissionIri: PermissionIri): Task[Option[DefaultObjectAccessPermission]] =
    repo.findById(permissionIri)

  def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]] =
    repo.findByProject(projectIri)

  def create(
    project: KnoraProject,
    forWhat: ForWhat,
    permission: Chunk[DefaultObjectAccessPermissionPart],
  ): Task[DefaultObjectAccessPermission] =
    repo.save(DefaultObjectAccessPermission(PermissionIri.makeNew(project.shortcode), project.id, forWhat, permission))

  def save(doap: DefaultObjectAccessPermission): Task[DefaultObjectAccessPermission] = repo.save(doap)

  def findByProjectAndForWhat(projectIri: ProjectIri, forWhat: ForWhat): Task[Option[DefaultObjectAccessPermission]] =
    repo.findByProjectAndForWhat(projectIri, forWhat)

  def asDefaultObjectAccessPermissionADM(doap: DefaultObjectAccessPermission): DefaultObjectAccessPermissionADM =
    DefaultObjectAccessPermissionADM(
      doap.id.value,
      doap.forProject.value,
      doap.forWhat.groupOption.map(_.value),
      doap.forWhat.resourceClassOption.map(_.value),
      doap.forWhat.propertyOption.map(_.value),
      asPermissionADM(doap.permission).toSet,
    )

  def asPermissionADM(parts: Chunk[DefaultObjectAccessPermissionPart]): Chunk[PermissionADM] =
    parts.flatMap { part =>
      part.groups.map(group =>
        PermissionADM(
          part.permission.token,
          Some(group.value),
          Some(part.permission.code),
        ),
      )
    }

  def delete(entity: DefaultObjectAccessPermission): Task[Unit] = for {
    _ <- ZIO
           .fail(UpdateNotPerformedException(s"Permission ${entity.id} is in use and cannot be deleted."))
           .whenZIO(triplestore.isIriInObjectPosition(Rdf.iri(entity.id.value)))
    _ <- repo.delete(entity)
  } yield ()
}

object DefaultObjectAccessPermissionService {
  val layer = ZLayer.derive[DefaultObjectAccessPermissionService]
}
