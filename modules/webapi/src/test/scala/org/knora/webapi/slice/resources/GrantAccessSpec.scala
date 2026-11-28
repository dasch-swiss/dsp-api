/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources

import org.junit.runner.RunWith
import zio.test.*

import scala.compiletime.testing.Error
import scala.compiletime.testing.typeCheckErrors

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.resources.access.Grant
import org.knora.webapi.slice.resources.access.Unfiltered
import org.knora.webapi.slice.resources.access.ValueToCreate

/**
 * Pins that code outside `slice.resources.access` can neither forge nor copy a grant, its target or the unfiltered
 * resource, nor read the unfiltered resource. This spec must stay outside that package and its subpackages: inside,
 * every probe compiles and the spec proves nothing.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class GrantAccessSpec extends ZIOSpecDefault {

  val user: User                  = TestDataFactory.User.rootUser
  val project: KnoraProject       = TestDataFactory.someProject
  def resource: ReadResourceV2    = ???
  def grant: Grant[ValueToCreate] = ???
  def unfiltered: Unfiltered      = ???
  def target: ValueToCreate       = ???

  private def isDenied(errors: List[Error], symbol: String, fragment: String) =
    assertTrue(errors.exists(e => e.message.contains(symbol) && e.message.contains(fragment)))

  val spec: Spec[Any, Any] = suite("Grant and Unfiltered outside slice.resources.access")(
    test("a Grant cannot be constructed") {
      isDenied(
        typeCheckErrors("new Grant(user, project, target, unfiltered)"),
        "Grant",
        "cannot be accessed",
      )
    },
    test("a Grant cannot be copied") {
      isDenied(typeCheckErrors("grant.copy(caller = user)"), "copy", "is not a member")
    },
    test("a Grant's unfiltered resource cannot be read") {
      isDenied(typeCheckErrors("grant.unfiltered"), "unfiltered", "cannot be accessed")
    },
    test("an Unfiltered cannot be constructed") {
      isDenied(typeCheckErrors("new Unfiltered(Some(resource))"), "Unfiltered", "cannot be accessed")
    },
    test("an Unfiltered cannot be read") {
      isDenied(typeCheckErrors("unfiltered.resource"), "resource", "cannot be accessed")
    },
    test("an integrity rule outside the package cannot read the unfiltered resource") {
      isDenied(typeCheckErrors("grant.verify(u => u.resource.map(_ => ???))"), "resource", "cannot be accessed")
    },
    test("a target cannot be constructed with new") {
      isDenied(typeCheckErrors("new ValueToCreate(resource)"), "ValueToCreate", "cannot be accessed")
    },
    test("a target cannot be constructed with apply") {
      isDenied(typeCheckErrors("ValueToCreate(resource)"), "ValueToCreate", "does not take parameters")
    },
    test("a target cannot be copied") {
      isDenied(typeCheckErrors("target.copy(resource = resource)"), "copy", "cannot be accessed")
    },
    test("a Grant's caller, project and target stay readable") {
      val errors = typeCheckErrors(
        "{ val u: User = grant.caller; val p: KnoraProject = grant.project; val r: ReadResourceV2 = grant.target.resource }",
      )
      assertTrue(errors.isEmpty)
    },
  )
}
