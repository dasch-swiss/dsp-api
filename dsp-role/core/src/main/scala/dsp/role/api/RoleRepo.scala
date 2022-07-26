/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.api

import dsp.role.domain.Role
import dsp.valueobjects.Id._
import zio._
import zio.macros.accessible

/**
 * The trait (interface) for the role repository.
 * The role repository is responsible for storing and retrieving roles.
 * Needs to be used by the role repository implementations.
 */
@accessible
trait RoleRepo {

  /**
   * Writes a role into the repository, while both creating or updating a role.
   *
   * @param r the role to write
   * @return Unit
   */
  def storeRole(r: Role): UIO[RoleId]

  /**
   * Gets all roles from teh repository.
   *
   * @return a list of [[Role]]
   */
  def getRoles(): UIO[List[Role]]

  /**
   * Retrieves the role frim the repository.
   *
   * @param id the role's ID
   * @return the [[Role]] if found
   */
  def getRoleById(id: RoleId): IO[Option[Nothing], Role]

  // should the role name be unique like username???

  /**
   * Deletes the [[Role]] from the repository by its [[RoleId]]
   *
   * @param id the role ID
   * @return the [[RoleId]] deleted role, if found
   */
  def deleteRole(id: RoleId): IO[Option[Nothing], RoleId]
}
