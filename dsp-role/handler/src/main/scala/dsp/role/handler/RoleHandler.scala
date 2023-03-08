/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.handler

import zio.ZIO
import zio._

import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import dsp.role.api.RoleRepo
import dsp.role.domain.Role
import dsp.role.domain.RoleUser
import dsp.valueobjects.Id.RoleId
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._

/**
 * The role handler.
 *
 * @param repo the role repository
 */
final case class RoleHandler(repo: RoleRepo) {

  /**
   * Retrieves all roles (sorted by IRI).
   */
  def getRoles(): UIO[List[Role]] =
    repo
      .getRoles()
      .map(_.sorted)
      .tap(_ => ZIO.logInfo("Retrieved all roles."))

  /**
   * Retrieves the role by ID.
   *
   * @param id of the role to be retrieved
   */
  def getRoleById(id: RoleId): IO[NotFoundException, Role] =
    for {
      role <- repo
                .getRoleById(id)
                .mapError(_ => NotFoundException(s"Not found the role with ID: $id"))
                .tap(_ => ZIO.logInfo(s"Found the role by ID: $id"))
    } yield role

  /**
   * Creates a new role.
   *
   * @param name the role's name
   * @param description the role's description
   * @param users the role's users
   * @param permission the role's permission
   * @return the [[RoleId]] if succeed, [[Throwable]] if fail
   */
  def createRole(
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

  /**
   * Updates the name of a role.
   *
   * @param id the roles's ID
   * @param newValue the new name value
   * @return the [[RoleId]] if succeed, [[RequestRejectedException]] if fail
   */
  def updateName(id: RoleId, newValue: LangString): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateName(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated name with new value: ${newValue.value}"))

  /**
   * Updates the description of a role.
   *
   * @param id the roles's ID
   * @param newValue the new description value
   * @return the [[RoleId]] if succeed, [[RequestRejectedException]] if fail
   */
  def updateDescription(id: RoleId, newValue: LangString): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateDescription(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated description with new value: $newValue"))

  /**
   * Updates the users of a role.
   *
   * @param id the roles's ID
   * @param newValue the new users value
   * @return the [[RoleId]] if succeed, [[RequestRejectedException]] if fail
   */
  def updateUsers(id: RoleId, newValue: List[RoleUser]): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updateUsers(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated users with new value: $newValue"))

  /**
   * Updates the permission of a role.
   *
   * @param id the roles's ID
   * @param newValue the new permission value
   * @return the [[RoleId]] if succeed, [[RequestRejectedException]] if fail
   */
  def updatePermission(id: RoleId, newValue: Permission): IO[RequestRejectedException, RoleId] =
    (for {
      role        <- getRoleById(id)
      updatedRole <- role.updatePermission(newValue).toZIO
      _           <- repo.storeRole(updatedRole)
    } yield id).tap(_ => ZIO.logInfo(s"Updated permission with new value: $newValue"))

  /**
   * Deletes the role.
   *
   * @param id the roles's ID
   * @return the [[RoleId]] if succeed, [[NotFoundException]] if fail
   */
  def deleteRole(id: RoleId): IO[NotFoundException, RoleId] =
    (for {
      _ <- repo.deleteRole(id).mapError(_ => NotFoundException(s"Not found the role with ID: $id"))
    } yield id).tap(_ => ZIO.logInfo(s"Deleted role with ID: $id"))
}

object RoleHandler {
  val layer: ZLayer[RoleRepo, Nothing, RoleHandler] =
    ZLayer {
      for {
        repo <- ZIO.service[RoleRepo]
      } yield RoleHandler(repo)
    }.tap(_ => ZIO.logInfo(">>> Role Handler initilaized <<<"))
}
