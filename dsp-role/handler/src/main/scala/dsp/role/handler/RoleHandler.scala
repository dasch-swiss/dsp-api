/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.handler

import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import dsp.role.api.RoleRepo
import dsp.role.domain.Role
import dsp.role.domain.RoleUser
import dsp.valueobjects.Id.RoleId
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._
import zio.ZIO
import zio._

/**
 * The role handler.
 *
 * @param repo  the role repository
 */
final case class RoleHandler(repo: RoleRepo) {

  def getRoles(): UIO[List[Role]] =
    repo
      .getRoles()
      .map(_.sorted)
      .tap(_ => ZIO.logInfo("Retrieved all roles."))

  def getRoleById(id: RoleId): IO[NotFoundException, Role] =
    for {
      role <- repo
                .getRoleById(id)
                .mapError(_ => NotFoundException(s"Role with ID: $id not found"))
                .tap(_ => ZIO.logInfo(s"Found the role by ID: $id"))
    } yield role

  def createRole(
    // id: RoleId,
    name: LangString,
    description: LangString,
    users: List[RoleUser],
    permission: Permission
  ): IO[Throwable, RoleId] =
    (for {
      id         <- RoleId.make().toZIO
      role       <- Role.make(id, name, description, users, permission).toZIO
      storedRole <- repo.storeRole(role)
    } yield storedRole).tap(id => ZIO.logInfo(s"Created role with ID: $id"))

  def updateName(id: RoleId, newValue: LangString): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateName(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated name with new value: ${newValue.value}"))

  def updateDescription(id: RoleId, newValue: LangString): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateDescription(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated description with new value: $newValue"))

  def updateUsers(id: RoleId, newValue: List[RoleUser]): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateUsers(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated users with new value: $newValue"))

  def updatePermissionn(id: RoleId, newValue: Permission): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updatePermission(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated permission with new value: $newValue"))

  def deleteRole(id: RoleId): IO[NotFoundException, RoleId] =
    (for {
      _ <- repo.deleteRole(id).mapError(_ => NotFoundException(s"Role with ID: $id not found"))
    } yield id).tap(_ => ZIO.logInfo(s"Deleted role with ID: $id"))
}

object RoleHandler {
  val layer: ZLayer[RoleRepo, Nothing, RoleHandler] = {
    ZLayer {
      for {
        repo <- ZIO.service[RoleRepo]
      } yield RoleHandler(repo)
    }.tap(_ => ZIO.logInfo(">>> Role Handler initilaized <<<"))
  }
}
