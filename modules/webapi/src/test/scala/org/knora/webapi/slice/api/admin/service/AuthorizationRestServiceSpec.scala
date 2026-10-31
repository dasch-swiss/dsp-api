/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import org.junit.runner.RunWith
import zio.Exit
import zio.ZIO
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.ForbiddenException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoInMemory
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.TestCandidates
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.OtelSetup
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceLive

@RunWith(classOf[DspZTestJUnitRunner])
class AuthorizationRestServiceSpec extends ZIOSpecDefault {

  private val authorizationRestService = ZIO.serviceWithZIO[AuthorizationRestService]

  private val activeNormalUser =
    User("http://iri", "username", "email@example.com", "given name", "family name", status = true, "lang")

  private val inactiveNormalUser = activeNormalUser.copy(status = false)

  private val activeSystemAdmin =
    activeNormalUser.copy(permissions =
      PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
    )

  private val inactiveSystemAdmin = activeSystemAdmin.copy(status = false)

  private val project = TestDataFactory.someProject

  private def userInGroupOf(projectIri: String, groupIri: String): User =
    activeNormalUser.copy(permissions = PermissionsDataADM(Map(projectIri -> List(groupIri))))

  private val projectAdmin      = userInGroupOf(project.id.value, KnoraGroupRepo.builtIn.ProjectAdmin.id.value)
  private val projectMember     = userInGroupOf(project.id.value, KnoraGroupRepo.builtIn.ProjectMember.id.value)
  private val otherProjectAdmin =
    userInGroupOf("http://rdfh.ch/projects/other", KnoraGroupRepo.builtIn.ProjectAdmin.id.value)

  private def authorizeProjectAdminById(user: User) =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)) *>
      authorizationRestService(_.authorizeProjectAdminById(TestCandidates.of(user), project.id))

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
          _          <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsAdmin =
            activeNormalUser.copy(permissions =
              PermissionsDataADM(Map(project.id.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
            )
          actualProject <- authorizationRestService(_.ensureSystemAdminOrProjectAdminById(userIsAdmin, project.id))
        } yield assertTrue(project == actualProject)
      },
      test(
        "and given the project does not exists for which the user is project admin when ensureSystemAdminOrProjectAdmin then succeed",
      ) {
        val project     = TestDataFactory.someProject
        val userIsAdmin =
          activeNormalUser.copy(permissions =
            PermissionsDataADM(Map(project.id.value -> List(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))),
          )
        for {
          exit <- authorizationRestService(_.ensureSystemAdminOrProjectAdminById(userIsAdmin, project.id)).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
      test(
        "and given a project for which the user is _not_ project admin  when ensureSystemAdminOrProjectAdmin then fail",
      ) {
        val project = TestDataFactory.someProject
        for {
          _             <- ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project))
          userIsNotAdmin = activeNormalUser.copy(permissions = PermissionsDataADM(Map.empty))
          exit          <- authorizationRestService(_.ensureSystemAdminOrProjectAdminById(userIsNotAdmin, project.id)).exit
        } yield assert(exit)(failsWithA[ForbiddenException])
      },
    ),
    suite("authorizeProjectAdminById")(
      test("issues a grant for the project to an active system admin") {
        authorizeProjectAdminById(activeSystemAdmin).map(grant =>
          assertTrue(grant.user == activeSystemAdmin, grant.project == project),
        )
      },
      test("issues a grant for the project to the project's admin") {
        authorizeProjectAdminById(projectAdmin).map(grant =>
          assertTrue(grant.user == projectAdmin, grant.project == project),
        )
      },
      test("refuses a member of the project") {
        authorizeProjectAdminById(projectMember).exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))
      },
      test("refuses an admin of another project") {
        authorizeProjectAdminById(otherProjectAdmin).exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))
      },
      test("refuses the anonymous user") {
        authorizeProjectAdminById(AnonymousUser).exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))
      },
      test("refuses an inactive system admin") {
        authorizeProjectAdminById(inactiveSystemAdmin).exit.map(exit =>
          assertTrue(exit == Exit.fail(ForbiddenException("The account with username 'username' is not active."))),
        )
      },
    ),
  ).provide(
    AppConfig.layer,
    AuthorizationRestService.layer,
    CacheManager.layer,
    IriConverter.layer,
    IriService.layer,
    KnoraGroupRepoInMemory.layer,
    KnoraGroupService.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraProjectService.layer,
    KnoraUserRepoLive.layer,
    KnoraUserService.layer,
    LicenseRepo.layer,
    OntologyRepoInMemory.emptyLayer,
    PasswordService.layer,
    StringFormatter.live,
    TriplestoreServiceLive.layer,
    OtelSetup.stdOut,
  )
}
