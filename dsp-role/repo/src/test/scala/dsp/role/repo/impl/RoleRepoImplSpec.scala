/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.repo.impl

import dsp.role.api.RoleRepo
import dsp.role.repo.impl.RoleRepoLive
import dsp.role.repo.impl.RoleRepoMock
import dsp.role.sharedtestdata.RoleTestData
import zio._
import zio.test.Assertion._
import zio.test._

/**
 * This spec is used to test all [[RoleRepo]] implementations.
 */
object RoleRepoImplSpec extends ZIOSpecDefault {

  def spec = (roleRepoMockTests + roleRepoLiveTests)

  private val role1 = RoleTestData.role1
  private val role2 = RoleTestData.role2

  val roleTests =
    test("store two roles and retrieve them all") {
      for {
        role1 <- role1.toZIO
        role2 <- role2.toZIO
        _     <- RoleRepo.storeRole(role1)
        _     <- RoleRepo.storeRole(role2)
        roles <- RoleRepo.getRoles()
      } yield assertTrue(roles.size == 2)
    } +
      test("store a role and get it by ID") {
        for {
          role1 <- role1.toZIO
          _     <- RoleRepo.storeRole(role1)
          role  <- RoleRepo.getRoleById(role1.id)
        } yield assertTrue(role == role1)
      } +
      test("store and delete a role") {
        for {
          role1           <- role1.toZIO
          roleId          <- RoleRepo.storeRole(role1)
          idOfDeletedRole <- RoleRepo.deleteRole(roleId)
          isIdDeleted     <- RoleRepo.getRoleById(idOfDeletedRole).exit
        } yield assertTrue(roleId == role1.id) &&
          assert(isIdDeleted)(fails(equalTo(None)))
      }

  val roleRepoMockTests = suite("RoleRepoMock")(
    roleTests
  ).provide(RoleRepoMock.layer)

  val roleRepoLiveTests = suite("RoleRepoLive")(
    roleTests
  ).provide(RoleRepoLive.layer)
}
