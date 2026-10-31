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

  private def messages(errors: List[scala.compiletime.testing.Error]): String = errors.map(_.message).mkString("\n")

  val spec: Spec[Any, Any] = suite("ProjectCandidate and ProjectAdminGrant outside slice.common.api")(
    test("a ProjectCandidate cannot be constructed") {
      val errors = typeCheckErrors("new ProjectCandidate(user)")
      assertTrue(messages(errors).contains("constructor ProjectCandidate cannot be accessed"))
    },
    test("a ProjectCandidate's user cannot be read") {
      val errors = typeCheckErrors("candidate.user")
      assertTrue(messages(errors).contains("value user cannot be accessed"))
    },
    test("a ProjectAdminGrant cannot be constructed with new") {
      val errors = typeCheckErrors("new ProjectAdminGrant(user, project)")
      assertTrue(messages(errors).contains("constructor ProjectAdminGrant cannot be accessed"))
    },
    test("a ProjectAdminGrant cannot be constructed with apply") {
      val errors = typeCheckErrors("ProjectAdminGrant(user, project)")
      assertTrue(
        messages(errors).contains(
          "object ProjectAdminGrant in package org.knora.webapi.slice.common.api does not take parameters",
        ),
      )
    },
    test("a ProjectAdminGrant cannot be copied") {
      val errors = typeCheckErrors("grant.copy(user = user)")
      assertTrue(messages(errors).contains("method copy cannot be accessed"))
    },
    test("a ProjectAdminGrant's fields and unapply stay readable") {
      val errors = typeCheckErrors(
        "{ val u: User = grant.user; val p: KnoraProject = grant.project; grant match { case ProjectAdminGrant(_, _) => () } }",
      )
      assertTrue(errors.isEmpty)
    },
  )
}
