/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.domain

import dsp.errors.BadRequestException
import dsp.valueobjects.Id.RoleId
import dsp.valueobjects.Id.UserId
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._
import zio.prelude.Validation

case class RoleUser(
  id: UserId
)

/**
 * Role's domain entity.
 *
 * @param id the role's ID
 * @param name the role's name
 * @param description the role's description
 * @param users the role's users
 * @param permission the role's permission
 */
sealed abstract case class Role private (
  id: RoleId,
  name: LangString,
  description: LangString,
  users: List[RoleUser], // how to reperesnt the user here?
  permission: Permission
) extends Ordered[Role] { self =>

  /**
   * Allows to compare the [[Role]] instances.
   *
   * @param that [[Role]] to compare
   * @return [[Boolean]] value
   */
  def compare(that: Role): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  /**
   * Updates the role's name with new value.
   *
   * @param newValue new role's name to update
   * @return updated [[Role]]
   */
  def updateName(newValue: LangString): Validation[BadRequestException, Role] =
    Role.make(
      self.id,
      newValue,
      self.description,
      self.users,
      self.permission
    )

  /**
   * Updates the role's description with new value.
   *
   * @param newValue new role's description to update
   * @return updated [[Role]]
   */
  def updateDescription(newValue: LangString): Validation[BadRequestException, Role] =
    Role.make(
      self.id,
      self.name,
      newValue,
      self.users,
      self.permission
    )

  /**
   * Updates the role's users with new value.
   *
   * @param newValue new role's users to update
   * @return updated [[Role]]
   */
  def updateUsers(newValue: List[RoleUser]): Validation[BadRequestException, Role] =
    Role.make(
      self.id,
      self.name,
      self.description,
      newValue,
      self.permission
    )

  /**
   * Updates the role's permission with new value.
   *
   * @param newValue new role's permission to update
   * @return updated [[Role]]
   */
  def updatePermission(newValue: Permission): Validation[BadRequestException, Role] =
    Role.make(
      self.id,
      self.name,
      self.description,
      self.users,
      newValue
    )
}

object Role {
  def make(
    id: RoleId,
    name: LangString,
    description: LangString,
    users: List[RoleUser],
    permission: Permission
  ): Validation[BadRequestException, Role] =
    Validation.succeed(
      new Role(
        id,
        name,
        description,
        users,
        permission
      ) {}
    )
}
