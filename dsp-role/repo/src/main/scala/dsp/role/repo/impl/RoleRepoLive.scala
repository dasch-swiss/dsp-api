/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.repo.impl

import dsp.role.api.RoleRepo
import dsp.role.domain.Role
import dsp.valueobjects.Id.RoleId
import zio._
import zio.stm.TMap

import java.util.UUID

/**
 * Role repo live implementation.
 *
 * @param roles a map of roles
 */
final case class RoleRepoLive(
  roles: TMap[UUID, Role]
) extends RoleRepo {

  /**
   * @inheritDoc
   */
  override def storeRole(r: Role): UIO[RoleId] =
    (for {
      _ <- roles.put(r.id.uuid, r)
    } yield r.id).commit.tap(_ => ZIO.logInfo(s"Stored role with ID: ${r.id.uuid}"))

  /**
   * @inheritDoc
   */
  override def getRoles(): UIO[List[Role]] =
    roles.values.commit.tap(rolesList => ZIO.logInfo(s"Number of roles found: ${rolesList.size}"))

  /**
   * @inheritDoc
   */
  override def getRoleById(id: RoleId): IO[Option[Nothing], Role] =
    roles
      .get(id.uuid)
      .commit
      .some
      .tapBoth(
        _ => ZIO.logInfo(s"Not found the role with ID: ${id.uuid}"),
        _ => ZIO.logInfo(s"Found role by ID: ${id.uuid}")
      )

  /**
   * @inheritDoc
   */
  override def deleteRole(id: RoleId): IO[Option[Nothing], RoleId] =
    (for {
      role <- roles.get(id.uuid).some
      _    <- roles.delete(id.uuid)
    } yield id).commit.tap(_ => ZIO.logInfo(s"Deleted role: ${id.uuid}"))
}

/**
 * Companion object providing the layer with an initialized implementation of [[RoleRepo]]
 */
object RoleRepoLive {
  val layer: ZLayer[Any, Nothing, RoleRepo] =
    ZLayer {
      for {
        roles <- TMap.empty[UUID, Role].commit
      } yield RoleRepoLive(roles)
    }.tap(_ => ZIO.logInfo(">>> Role repository initialized <<<"))
}
