/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.domain

import dsp.valueobjects.Id
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._
import zio.test._

object RoleDomainSpec extends ZIOSpecDefault {
  def spec = (compareRolesTest + createRoleTest + updateRoleTest)

  private val compareRolesTest = suite("compareRoles")(
    test("compare two roles") {
      val role         = RoleTestData.role1
      val equalRole    = RoleTestData.role1
      val nonEqualRole = RoleTestData.role2

      assertTrue(role == equalRole) &&
      assertTrue(role != nonEqualRole)
    }
  )

  private val createRoleTest = suite("createRole")(
    test("create a role") {
      (
        for {
          id          <- RoleTestData.roleId1
          name        <- RoleTestData.roleName1
          description <- RoleTestData.roleDescription1
          users        = RoleTestData.roleUsers1
          permission  <- RoleTestData.rolePermission1

          role <- Role.make(
                    id,
                    name,
                    description,
                    users = users,
                    permission
                  )
        } yield assertTrue(role.id == id) &&
          assertTrue(role.name == name) &&
          assertTrue(role.description == description) &&
          assertTrue(role.users == users) &&
          assertTrue(role.permission == permission)
      ).toZIO
    }
  )

  private val updateRoleTest = suite("updateRole")(
    test("update the name") {
      (
        for {
          role        <- RoleTestData.role1
          newValue    <- LangString.make("newRoleName", "en")
          updatedRole <- role.updateName(newValue)
        } yield assertTrue(updatedRole.name == newValue) &&
          assertTrue(updatedRole.name != role.name) &&
          assertTrue(updatedRole.description == role.description) &&
          assertTrue(updatedRole.users == role.users) &&
          assertTrue(updatedRole.permission == role.permission)
      ).toZIO
    },
    test("update the description") {
      (
        for {
          role        <- RoleTestData.role1
          newValue    <- LangString.make("New Role Description", "en")
          updatedRole <- role.updateDescription(newValue)
        } yield assertTrue(updatedRole.name == role.name) &&
          assertTrue(updatedRole.description == newValue) &&
          assertTrue(updatedRole.description != role.description) &&
          assertTrue(updatedRole.users == role.users) &&
          assertTrue(updatedRole.permission == role.permission)
      ).toZIO
    },
    test("update the users") {
      (
        for {
          role        <- RoleTestData.role1
          newValue     = List(RoleUser(Id.UserId.make().fold(e => throw e.head, v => v)))
          updatedRole <- role.updateUsers(newValue)
        } yield assertTrue(updatedRole.name == role.name) &&
          assertTrue(updatedRole.description == role.description) &&
          assertTrue(updatedRole.users == newValue) &&
          assertTrue(updatedRole.users != role.users) &&
          assertTrue(updatedRole.permission == role.permission)
      ).toZIO
    },
    test("update the permission") {
      (
        for {
          role        <- RoleTestData.role1
          newValue    <- Permission.make(Permission.Create)
          updatedRole <- role.updatePermission(newValue)
        } yield assertTrue(updatedRole.name == role.name) &&
          assertTrue(updatedRole.description == role.description) &&
          assertTrue(updatedRole.users == role.users) &&
          assertTrue(updatedRole.permission == newValue) &&
          assertTrue(updatedRole.permission != role.permission)
      ).toZIO
    }
  )
}
