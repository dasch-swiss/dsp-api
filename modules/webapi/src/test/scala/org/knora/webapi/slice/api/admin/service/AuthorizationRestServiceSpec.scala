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
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
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

  private val unknownShortcode = Shortcode.unsafeFrom("9999")
  private val unknownShortname = Shortname.unsafeFrom("unknown")

  private def authorizeByShortcode(user: User, shortcode: Shortcode = project.shortcode) =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)) *>
      authorizationRestService(_.authorizeProjectAdminByShortcode(TestCandidates.of(user), shortcode))

  private def authorizeByShortname(user: User, shortname: Shortname = project.shortname) =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)) *>
      authorizationRestService(_.authorizeProjectAdminByShortname(TestCandidates.of(user), shortname))

  private def authorizeMemberByShortcode(user: User, shortcode: Shortcode = project.shortcode) =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)) *>
      authorizationRestService(_.authorizeProjectMemberByShortcode(TestCandidates.of(user), shortcode))

  private val group = TestDataFactory.UserGroup.testUserGroup

  private def authorizeOfGroup(user: User, groupIri: GroupIri = group.id) =
    ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(project)) *>
      ZIO.serviceWithZIO[KnoraGroupRepoInMemory](_.save(group)) *>
      authorizationRestService(_.authorizeProjectAdminOfGroup(TestCandidates.of(user), groupIri))

  private def refusedBy[R](zio: ZIO[R, Throwable, Any]) =
    zio.exit.map(exit => assert(exit)(failsWithA[ForbiddenException]))

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
    suite("authorizeProjectAdminByShortcode")(
      test("issues a grant for the project to an active system admin") {
        authorizeByShortcode(activeSystemAdmin).map(grant =>
          assertTrue(grant.user == activeSystemAdmin, grant.project == project),
        )
      },
      test("issues a grant for the project to the project's admin") {
        authorizeByShortcode(projectAdmin).map(grant =>
          assertTrue(grant.user == projectAdmin, grant.project == project),
        )
      },
      test("refuses a member of the project") {
        refusedBy(authorizeByShortcode(projectMember))
      },
      test("refuses an admin of another project") {
        refusedBy(authorizeByShortcode(otherProjectAdmin))
      },
      test("refuses an inactive system admin") {
        authorizeByShortcode(inactiveSystemAdmin).exit.map(exit =>
          assertTrue(exit == Exit.fail(ForbiddenException("The account with username 'username' is not active."))),
        )
      },
      test("refuses an unknown project") {
        refusedBy(authorizeByShortcode(activeSystemAdmin, unknownShortcode))
      },
    ),
    suite("authorizeProjectAdminByShortname")(
      test("issues a grant for the project to an active system admin") {
        authorizeByShortname(activeSystemAdmin).map(grant =>
          assertTrue(grant.user == activeSystemAdmin, grant.project == project),
        )
      },
      test("issues a grant for the project to the project's admin") {
        authorizeByShortname(projectAdmin).map(grant =>
          assertTrue(grant.user == projectAdmin, grant.project == project),
        )
      },
      test("refuses a member of the project") {
        refusedBy(authorizeByShortname(projectMember))
      },
      test("refuses an admin of another project") {
        refusedBy(authorizeByShortname(otherProjectAdmin))
      },
      test("refuses an inactive system admin") {
        authorizeByShortname(inactiveSystemAdmin).exit.map(exit =>
          assertTrue(exit == Exit.fail(ForbiddenException("The account with username 'username' is not active."))),
        )
      },
      test("refuses an unknown project") {
        refusedBy(authorizeByShortname(activeSystemAdmin, unknownShortname))
      },
    ),
    suite("authorizeProjectAdminOfGroup")(
      test("issues the group and a grant for its project to an active system admin") {
        authorizeOfGroup(activeSystemAdmin).map((g, grant) =>
          assertTrue(g == group, grant.user == activeSystemAdmin, grant.project == project),
        )
      },
      test("issues the group and a grant for its project to the project's admin") {
        authorizeOfGroup(projectAdmin).map((g, grant) =>
          assertTrue(g == group, grant.user == projectAdmin, grant.project == project),
        )
      },
      test("refuses a member of the group's project") {
        refusedBy(authorizeOfGroup(projectMember))
      },
      test("refuses an admin of another project") {
        refusedBy(authorizeOfGroup(otherProjectAdmin))
      },
      test("refuses an inactive system admin") {
        refusedBy(authorizeOfGroup(inactiveSystemAdmin))
      },
      test("refuses an unknown group") {
        refusedBy(authorizeOfGroup(activeSystemAdmin, GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/unknown")))
      },
      test("refuses a built-in group, which has no project, even for a system admin") {
        refusedBy(authorizeOfGroup(activeSystemAdmin, KnoraGroupRepo.builtIn.KnownUser.id))
      },
    ),
    suite("authorizeProjectMemberByShortcode")(
      test("issues a grant for the project to an active system admin") {
        authorizeMemberByShortcode(activeSystemAdmin).map(grant =>
          assertTrue(grant.user == activeSystemAdmin, grant.project == project),
        )
      },
      test("issues a grant for the project to the project's admin") {
        authorizeMemberByShortcode(projectAdmin).map(grant => assertTrue(grant.project == project))
      },
      test("issues a grant for the project to a member") {
        authorizeMemberByShortcode(projectMember).map(grant =>
          assertTrue(grant.user == projectMember, grant.project == project),
        )
      },
      test("refuses an admin of another project") {
        refusedBy(authorizeMemberByShortcode(otherProjectAdmin))
      },
      test("refuses a user without any membership") {
        refusedBy(authorizeMemberByShortcode(activeNormalUser))
      },
      test("refuses an inactive system admin") {
        refusedBy(authorizeMemberByShortcode(inactiveSystemAdmin))
      },
      test("refuses an unknown project") {
        refusedBy(authorizeMemberByShortcode(activeSystemAdmin, unknownShortcode))
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
