/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.domain

object Permission extends Enumeration {
  type Permission = Value

  val View   = Value("view")
  val Create = Value("create")
  val Modify = Value("modify")
  val Delete = Value("delete")
  val Admin  = Value("admin")
}

/**
 * Role's domain model.
 *
 * @param id the role's ID
 * @param name the role's name
 * @param description the role's description
 * @param users the role's users
 * @param permission the role's permission
 */
sealed abstract case class Role private (
  id: String,            // RoleId(RoleIri, Uuid)
  name: String,          // Langstring
  description: String,   // Langstring
  users: List[User],     // List[User]
  permission: Permission // Permission => view | create | modify | delete | administrate | erase
) { self =>

  /**
   * Allows to compare teo [[Role]] instances.
   *
   * @param that [[Role]] to compare
   * @return [[Boolean]] value
   */
  def equals(that: Role): Boolean = self.id.equals(that.id)

  /**
   * Updates the role's name.
   *
   * @param newValue new role's name to update
   * @return updated [[Role]]
   */
  def updateName(newValue: String): Role =
    Role.make(
      self.id,
      newValue,
      self.description,
      self.users,
      self.permission
    )

  /**
   * Updates the role's description.
   *
   * @param newValue new role's description to update
   * @return updated [[Role]]
   */
  def updateDescription(newValue: String): Role =
    Role.make(
      self.id,
      self.name,
      newValue,
      self.users,
      self.permission
    )

  /**
   * Updates the role's users.
   *
   * @param newValue new role's users to update
   * @return updated [[Role]]
   */
  def updateUsers(newValue: List[User]): Role =
    Role.make(
      self.id,
      self.name,
      self.description,
      newValue,
      self.permission
    )

  /**
   * Updates the role's permission.
   *
   * @param newValue new role's permission to update
   * @return updated [[Role]]
   */
  def updatePermission(newValue: Permission): Role =
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
    id: String,
    name: String,
    description: String,
    users: List[User],
    permission: Permission
  )
}
