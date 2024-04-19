/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Exit
import zio.ZIO
import zio.test.Assertion.failsWithA
import zio.test._
import dsp.errors.ForbiddenException
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.repo.EntityCache.CacheManager
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive

object AuthorizationRestServiceSpec extends ZIOSpecDefault {

  private val authorizationRestService = ZIO.serviceWithZIO[AuthorizationRestService]

  private val activeNormalUser =
    User("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")

  private val inactiveNormalUser = activeNormalUser.copy(status = false)

  private val activeSystemAdmin =
    activeNormalUser.copy(permissions =
      PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
    )

  private val inactiveSystemAdmin = activeSystemAdmin.copy(status = false)

  val spec: Spec[Any, Any] = suite("RestPermissionService")(
    suite("given an inactive system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(AuthorizationRestService.isSystemAdminOrUser(inactiveSystemAdmin))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- authorizationRestService(_.ensureSystemAdmin(inactiveSystemAdmin)).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active.")),
        )
      },
    ),
    suite("given a active system admin")(
      test("isSystemAdmin should return true") {
        assertTrue(AuthorizationRestService.isSystemAdminOrUser(activeSystemAdmin))
      },
      test("when ensureSystemAdmin succeed") {
        for {
          _ <- authorizationRestService(_.ensureSystemAdmin(activeSystemAdmin))
        } yield assertCompletes
      },
    ),
    suite("given an inactive normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!AuthorizationRestService.isSystemAdminOrUser(inactiveNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- authorizationRestService(_.ensureSystemAdmin(inactiveNormalUser)).exit
        } yield assertTrue(
          actual == Exit.fail(ForbiddenException("The account with username 'username' is not active.")),
        )
      },
    ),
    suite("given an active normal user")(
      test("isSystemAdmin should return false") {
        assertTrue(!AuthorizationRestService.isSystemAdminOrUser(activeNormalUser))
      },
      test("when ensureSystemAdmin fail with a ForbiddenException") {
        for {
          actual <- authorizationRestService(_.ensureSystemAdmin(activeNormalUser)).exit
        } yield assertTrue(
          actual == Exit.fail(
            ForbiddenException(
              "You are logged in with username 'username', but only a system administrator has permissions for this operation.",
            ),
          ),
        )
      },
      test(
        "and given a project for which the user is project admin when ensureSystemAdminOrProjectAdmin then succeed",
      ) {
        val project = TestDataFactory.someProject
        for {
          _ <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsAdmin =
            activeNormalUser.copy(permissions =
              PermissionsDataADM(Map(project.id.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
            )
          actualProject <- authorizationRestService(_.ensureSystemAdminOrProjectAdmin(userIsAdmin, project.id))
        } yield assertTrue(project == actualProject)
      },
      test(
        "and given the project does not exists for which the user is project admin when ensureSystemAdminOrProjectAdmin then succeed",
      ) {
        val project = TestDataFactory.someProject
        val userIsAdmin =
          activeNormalUser.copy(permissions =
            PermissionsDataADM(Map(project.id.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
          )
        for {
          exit <- authorizationRestService(_.ensureSystemAdminOrProjectAdmin(userIsAdmin, project.id)).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
      test(
        "and given a project for which the user is _not_ project admin  when ensureSystemAdminOrProjectAdmin then fail",
      ) {
        val project = TestDataFactory.someProject
        for {
          _             <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsNotAdmin = activeNormalUser.copy(permissions = PermissionsDataADM(Map.empty))
          exit          <- authorizationRestService(_.ensureSystemAdminOrProjectAdmin(userIsNotAdmin, project.id)).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
    ),
  ).provide(
    AppConfig.layer,
    AuthorizationRestService.layer,
    CacheService.layer,
    CacheManager.layer,
    IriConverter.layer,
    IriService.layer,
    KnoraGroupRepoInMemory.layer,
    KnoraGroupService.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraProjectService.layer,
    KnoraUserRepoLive.layer,
    KnoraUserService.layer,
    PasswordService.layer,
    StringFormatter.live,
    TriplestoreServiceLive.layer,
  )
}
