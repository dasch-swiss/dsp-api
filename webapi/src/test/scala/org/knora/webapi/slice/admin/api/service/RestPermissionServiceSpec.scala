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

  private val normalUser =
    UserADM("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")

  private val systemAdmin = normalUser.copy(permissions = PermissionsDataADM(Map(SystemProject -> List(SystemAdmin))))

  val spec: Spec[Any, ForbiddenException]#ZSpec[Any, ForbiddenException, TestSuccess] = suite("RestPermissionService")(
    suite("given a system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(RestPermissionService.isSystemAdmin(systemAdmin))
      },
      test("when ensureSystemAdmin succeed") {
        for {
          _ <- RestPermissionService.ensureSystemAdmin(systemAdmin)
        } yield assertCompletes
      }
    ),
    suite("given a normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!RestPermissionService.isSystemAdmin(normalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- RestPermissionService.ensureSystemAdmin(normalUser).exit
        } yield assertTrue(
          actual == Exit.fail(
            ForbiddenException(
              "You are logged in as username, but only a system administrator has permissions for this operation."
            )
          )
        )
      }
    )
  ).provide(RestPermissionServiceLive.layer)
}
