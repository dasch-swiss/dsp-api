/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.handler

import zio.ZIO
import zio.test._

import dsp.role.repo.impl.RoleRepoMock
import dsp.role.sharedtestdata.RoleTestData
import dsp.valueobjects.Permission
import dsp.valueobjects.Role._

/**
 * This spec is used to test [[RoleHandler]]
 */
object RoleHandlerSpec extends ZIOSpecDefault {
  def spec = (getRolesTest + updateRoleTest + deleteRoleTest)

  private val getRolesTest = suite("getRoles")(
    test("return an empty map while trying to get all roles but there are none") {
      for {
        handler <- ZIO.service[RoleHandler]
        roles   <- handler.getRoles()
      } yield assertTrue(roles.size == 0)
    },
    test("store some roles and retrive them all") {
      for {
        handler <- ZIO.service[RoleHandler]

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        name2        <- RoleTestData.name2.toZIO
        description2 <- RoleTestData.description2.toZIO
        users2        = RoleTestData.users2
        permission2  <- RoleTestData.permission2.toZIO

        _ <- handler.createRole(
               name1,
               description1,
               users1,
               permission1
             )

        _ <- handler.createRole(
               name2,
               description2,
               users2,
               permission2
             )

        roles <- handler.getRoles()
      } yield assertTrue(roles.size == 2)
    }
  ).provide(RoleRepoMock.layer, RoleHandler.layer)

  private val updateRoleTest = suite("updateRole")(
    test("store a role and update its name") {
      for {
        handler <- ZIO.service[RoleHandler]

        newValue <- LangString.make("New Role Name", "en").toZIO

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        roleId <- handler.createRole(
                    name1,
                    description1,
                    users1,
                    permission1
                  )

        updatedRoleId <- handler.updateName(roleId, newValue)
        retrievedRole <- handler.getRoleById(updatedRoleId)
      } yield assertTrue(retrievedRole.name == newValue) &&
        assertTrue(retrievedRole.description == description1) &&
        assertTrue(retrievedRole.users == users1) &&
        assertTrue(retrievedRole.permission == permission1)
    },
    test("store a role and update its description") {
      for {
        handler <- ZIO.service[RoleHandler]

        newValue <- LangString.make("New Role Description", "en").toZIO

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        roleId <- handler.createRole(
                    name1,
                    description1,
                    users1,
                    permission1
                  )

        updatedRoleId <- handler.updateDescription(roleId, newValue)
        retrievedRole <- handler.getRoleById(updatedRoleId)
      } yield assertTrue(retrievedRole.name == name1) &&
        assertTrue(retrievedRole.description == newValue) &&
        assertTrue(retrievedRole.users == users1) &&
        assertTrue(retrievedRole.permission == permission1)
    },
    test("store a role and update its name") {
      for {
        handler <- ZIO.service[RoleHandler]

        newValue <- LangString.make("New Role Name", "en").toZIO

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        roleId <- handler.createRole(
                    name1,
                    description1,
                    users1,
                    permission1
                  )

        updatedRoleId <- handler.updateName(roleId, newValue)
        retrievedRole <- handler.getRoleById(updatedRoleId)
      } yield assertTrue(retrievedRole.name == newValue) &&
        assertTrue(retrievedRole.description == description1) &&
        assertTrue(retrievedRole.users == users1) &&
        assertTrue(retrievedRole.permission == permission1)
    },
    test("store a role and update its permission") {
      for {
        handler <- ZIO.service[RoleHandler]

        newValue <- Permission.make(Permission.Create).toZIO

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        roleId <- handler.createRole(
                    name1,
                    description1,
                    users1,
                    permission1
                  )

        updatedRoleId <- handler.updatePermission(roleId, newValue)
        retrievedRole <- handler.getRoleById(updatedRoleId)
      } yield assertTrue(retrievedRole.name == name1) &&
        assertTrue(retrievedRole.description == description1) &&
        assertTrue(retrievedRole.users == users1) &&
        assertTrue(retrievedRole.permission == newValue)
    }
  ).provide(RoleRepoMock.layer, RoleHandler.layer)

  private val deleteRoleTest = suite("deleteRole") {
    test("store and delete a role") {
      for {
        handler <- ZIO.service[RoleHandler]

        name1        <- RoleTestData.name1.toZIO
        description1 <- RoleTestData.description1.toZIO
        users1        = RoleTestData.users1
        permission1  <- RoleTestData.permission1.toZIO

        roleId <- handler.createRole(
                    name1,
                    description1,
                    users1,
                    permission1
                  )

        _     <- handler.deleteRole(roleId)
        roles <- handler.getRoles()
      } yield assertTrue(roles.size == 0)
    }
  }.provide(RoleRepoMock.layer, RoleHandler.layer)
}
