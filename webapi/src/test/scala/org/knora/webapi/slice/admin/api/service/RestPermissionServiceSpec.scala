/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Exit
import zio.test.Spec
import zio.test.TestSuccess
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import dsp.errors.ForbiddenException
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.SystemAdmin
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.SystemProject
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

object RestPermissionServiceSpec extends ZIOSpecDefault {

  private val activeNormalUser =
    UserADM("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")

  private val inactiveNormalUser = activeNormalUser.copy(status = false)

  private val activeSystemAdmin =
    activeNormalUser.copy(permissions = PermissionsDataADM(Map(SystemProject -> List(SystemAdmin))))

  private val inactiveSystemAdmin = activeSystemAdmin.copy(status = false)

  val spec: Spec[Any, ForbiddenException]#ZSpec[Any, ForbiddenException, TestSuccess] = suite("RestPermissionService")(
    suite("given an inactive system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(RestPermissionService.isSystemAdmin(inactiveSystemAdmin))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- RestPermissionService.ensureSystemAdmin(inactiveSystemAdmin).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active."))
        )
      }
    ),
    suite("given a active system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(RestPermissionService.isSystemAdmin(activeSystemAdmin))
      },
      test("when ensureSystemAdmin succeed") {
        for {
          _ <- RestPermissionService.ensureSystemAdmin(activeSystemAdmin)
        } yield assertCompletes
      }
    ),
    suite("given an inactive normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!RestPermissionService.isSystemAdmin(inactiveNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- RestPermissionService.ensureSystemAdmin(inactiveNormalUser).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active."))
        )
      }
    ),
    suite("given an active normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!RestPermissionService.isSystemAdmin(activeNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- RestPermissionService.ensureSystemAdmin(activeNormalUser).exit
        } yield assertTrue(
          actual == Exit.fail(
            ForbiddenException(
              "You are logged in with username 'username', but only a system administrator has permissions for this operation."
            )
          )
        )
      }
    )
  ).provide(RestPermissionServiceLive.layer)
}
