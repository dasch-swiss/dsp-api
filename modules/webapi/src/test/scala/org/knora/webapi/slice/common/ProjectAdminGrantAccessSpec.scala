/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import org.junit.runner.RunWith
import zio.test.*

import scala.compiletime.testing.typeCheckErrors

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.ProjectAdminGrant
import org.knora.webapi.slice.common.api.ProjectCandidate
import org.knora.webapi.slice.common.api.TestCandidates

/**
 * Pins that code outside `slice.common.api` can neither forge a candidate or grant nor read a candidate's user. This
 * spec must stay outside that package and its subpackages: inside, every probe compiles and the spec proves nothing.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ProjectAdminGrantAccessSpec extends ZIOSpecDefault {

  val user: User                  = TestDataFactory.User.rootUser
  val project: KnoraProject       = TestDataFactory.someProject
  val candidate: ProjectCandidate = TestCandidates.of(user)
  def grant: ProjectAdminGrant    = ???

  private def isDenied(errors: List[scala.compiletime.testing.Error], symbol: String, fragment: String) =
    assertTrue(errors.exists(e => e.message.contains(symbol) && e.message.contains(fragment)))

  val spec: Spec[Any, Any] = suite("ProjectCandidate and ProjectAdminGrant outside slice.common.api")(
    test("a ProjectCandidate cannot be constructed") {
      isDenied(typeCheckErrors("new ProjectCandidate(user)"), "ProjectCandidate", "cannot be accessed")
    },
    test("a ProjectCandidate's user cannot be read") {
      isDenied(typeCheckErrors("candidate.user"), "user", "cannot be accessed")
    },
    test("a ProjectAdminGrant cannot be constructed with new") {
      isDenied(typeCheckErrors("new ProjectAdminGrant(user, project)"), "ProjectAdminGrant", "cannot be accessed")
    },
    test("a ProjectAdminGrant cannot be constructed with apply") {
      isDenied(typeCheckErrors("ProjectAdminGrant(user, project)"), "ProjectAdminGrant", "does not take parameters")
    },
    test("a ProjectAdminGrant cannot be copied") {
      isDenied(typeCheckErrors("grant.copy(user = user)"), "copy", "cannot be accessed")
    },
    test("a ProjectAdminGrant's fields and unapply stay readable") {
      val errors = typeCheckErrors(
        "{ val u: User = grant.user; val p: KnoraProject = grant.project; grant match { case ProjectAdminGrant(_, _) => () } }",
      )
      assertTrue(errors.isEmpty)
    },
  )
}
