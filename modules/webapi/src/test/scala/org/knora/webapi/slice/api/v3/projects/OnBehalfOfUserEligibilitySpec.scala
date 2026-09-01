/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo

@RunWith(classOf[DspZTestJUnitRunner])
class OnBehalfOfUserEligibilitySpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0803")

  private val base: User = User(
    id = "http://rdfh.ch/users/testeligibility",
    username = "testeligibility",
    email = "test.eligibility@example.com",
    givenName = "Test",
    familyName = "Eligibility",
    status = true,
    lang = "en",
  )

  /** A project member, parameterised over the two conditions no shared fixture combines. */
  private def member(active: Boolean, canCreate: Boolean): User =
    base.copy(
      status = active,
      permissions = PermissionsDataADM(
        groupsPerProject = Map(projectIri.value -> List(KnoraGroupRepo.builtIn.ProjectMember.id.value)),
        administrativePermissionsPerProject =
          if (canCreate)
            Map(projectIri.value -> Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)))
          else Map.empty,
      ),
    )

  private val systemAdmin: User =
    base.copy(
      permissions = PermissionsDataADM(
        groupsPerProject = Map(
          KnoraProjectRepo.builtIn.SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value),
        ),
      ),
    )

  /** No project memberships at all. */
  private val nonMember: User = base

  override def spec: Spec[Any, Any] = suite("OnBehalfOfUserEligibility.check")(
    test("accepts an active project member with project-wide create rights") {
      assertTrue(OnBehalfOfUserEligibility.check(member(active = true, canCreate = true), projectIri).isEmpty)
    },
    test("rejects a system admin (REQ-2.4)") {
      assertTrue(
        OnBehalfOfUserEligibility.check(systemAdmin, projectIri).contains(OnBehalfOfIneligibility.IsSystemAdmin),
      )
    },
    test("rejects a user who is not a member of the project (REQ-2.5)") {
      assertTrue(
        OnBehalfOfUserEligibility.check(nonMember, projectIri).contains(OnBehalfOfIneligibility.NotProjectMember),
      )
    },
    test("rejects an inactive member (G3 — no shared fixture exercises this)") {
      assertTrue(
        OnBehalfOfUserEligibility
          .check(member(active = false, canCreate = true), projectIri)
          .contains(
            OnBehalfOfIneligibility.Inactive,
          ),
      )
    },
    test("rejects a member without project-wide create rights (REQ-2.6, D6)") {
      assertTrue(
        OnBehalfOfUserEligibility
          .check(member(active = true, canCreate = false), projectIri)
          .contains(
            OnBehalfOfIneligibility.CannotCreate,
          ),
      )
    },
    test("reason strings are the stable cross-repo contract read by dsp-tools") {
      assertTrue(
        OnBehalfOfIneligibility.MalformedIdentifier.reason == "malformed_identifier",
        OnBehalfOfIneligibility.IsSystemAdmin.reason == "is_system_admin",
        OnBehalfOfIneligibility.NotProjectMember.reason == "not_project_member",
        OnBehalfOfIneligibility.Inactive.reason == "inactive",
        OnBehalfOfIneligibility.CannotCreate.reason == "cannot_create",
      )
    },
  )
}
