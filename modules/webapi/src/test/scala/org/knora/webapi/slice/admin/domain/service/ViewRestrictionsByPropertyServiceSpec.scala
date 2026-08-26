/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Audience
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility

/**
 * Tests for [[ViewRestrictionsByPropertyService]].
 *
 * The suite that matters here is the equivalence pin (PRD REQ-6.3). The permission machinery is duplicated
 * between this service and [[ViewRestrictionsService]] on purpose — sharing it would reintroduce the
 * `groupBy` seam the split exists to remove — and the cost of that decision is that a subtle argument now
 * has to hold in two places. This is what turns a divergence into a failing test rather than two screens
 * quietly disagreeing.
 *
 * These tests deliberately need no triplestore and no ontology: the property under test is a property of
 * the permission model, not of any project's data.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyServiceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  /**
   * The service instance under test.
   *
   * Constructed with `null` collaborators because every method exercised below — `audienceUser` and
   * `visibilityOf` — is a pure function of its arguments and never touches the repo. Wiring a real
   * `OntologyRepo` here would drag in the ontology cache and its whole layer stack to test arithmetic on a
   * permission string. If a future test needs `properties`, `propertyValues` or `propertyItems`, it wants
   * the full layer set and belongs in its own suite.
   */
  private val service = ViewRestrictionsByPropertyService(null)

  /**
   * A creator IRI that is emphatically not one of the synthetic audience users.
   *
   * The equivalence being pinned is that classifying a bare literal against a placeholder creator gives the
   * same answer as classifying a real object whose creator is known — which holds precisely because none of
   * the three audiences is ever the creator.
   */
  private val realCreator = "http://rdfh.ch/users/some-real-person"

  /**
   * Literals spanning the cases the report distinguishes, including the one that makes the argument
   * non-obvious: `CR knora-admin:Creator` grants the creator full rights and everyone else nothing, so it
   * is exactly the literal where a placeholder creator could have diverged from a real one.
   */
  private val literals = List(
    "V knora-admin:UnknownUser",
    "M knora-admin:ProjectMember",
    "V knora-admin:KnownUser|M knora-admin:ProjectMember",
    "RV knora-admin:UnknownUser|V knora-admin:KnownUser",
    "CR knora-admin:Creator",
    "CR knora-admin:Creator|V knora-admin:UnknownUser",
  )

  private def visibilityWith(creator: String, literal: String, audience: Audience): Visibility =
    service.visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = literal,
        requestingUser = service.audienceUser(audience, projectIri),
      ),
    )

  private val equivalenceSuite = suite("bare-literal classification (REQ-6.3)")(
    // THE pin. The counts classify a literal with a placeholder creator; the drill-down resolves the same
    // object with its real creator. If those ever disagree, a property's count and its drill-down would
    // contradict each other, and neither would obviously be wrong.
    test("classifying a bare literal equals classifying a real object, for every audience") {
      val mismatches = for {
        literal        <- literals
        audience       <- Audience.ordered
        withPlaceholder = visibilityWith("urn:view-restrictions-by-property:no-such-creator", literal, audience)
        withReal        = visibilityWith(realCreator, literal, audience)
        if withPlaceholder != withReal
      } yield s"$literal / $audience: placeholder=$withPlaceholder real=$withReal"
      assertTrue(mismatches.isEmpty)
    },
    // The consequence that makes the equivalence hold, stated directly so the reasoning is not implicit.
    test("a Creator-only literal grants nothing to any audience") {
      val results = Audience.ordered.map(a => visibilityWith(realCreator, "CR knora-admin:Creator", a))
      assertTrue(results.forall(_ == Visibility.Hidden))
    },
    test("no audience user is ever the creator, which is why the placeholder is sound") {
      val ids = Audience.ordered.map(a => service.audienceUser(a, projectIri).id)
      assertTrue(ids.forall(_ != realCreator), ids.distinct.nonEmpty)
    },
  )

  private val visibilitySuite = suite("visibilityOf")(
    test("no permission at all is Hidden") {
      assertTrue(service.visibilityOf(None) == Visibility.Hidden)
    },
    test("a project member sees an open resource, anonymous does not see a member-only one") {
      val open       = visibilityWith(realCreator, "V knora-admin:UnknownUser", Audience.Anonymous)
      val memberOnly = visibilityWith(realCreator, "M knora-admin:ProjectMember", Audience.Anonymous)
      val asMember   = visibilityWith(realCreator, "M knora-admin:ProjectMember", Audience.ProjectMember)
      assertTrue(
        open == Visibility.Visible,
        memberOnly == Visibility.Hidden,
        asMember == Visibility.Visible,
      )
    },
    test("restricted view is reported as its own state, not folded into hidden") {
      // The whole point of reporting two states: RV means a degraded version IS served, and collapsing it
      // into "hidden" would tell an admin nothing is served when something is.
      val rv = visibilityWith(realCreator, "RV knora-admin:UnknownUser|V knora-admin:KnownUser", Audience.Anonymous)
      assertTrue(rv == Visibility.RestrictedView)
    },
    test("access widens across the audiences, never narrows") {
      // The cumulative invariant the report's columns rely on.
      val order      = Map(Visibility.Hidden -> 0, Visibility.RestrictedView -> 1, Visibility.Visible -> 2)
      val violations = for {
        literal <- literals
        anon     = order(visibilityWith(realCreator, literal, Audience.Anonymous))
        auth     = order(visibilityWith(realCreator, literal, Audience.Authenticated))
        member   = order(visibilityWith(realCreator, literal, Audience.ProjectMember))
        if anon > auth || auth > member
      } yield s"$literal: anon=$anon auth=$auth member=$member"
      assertTrue(violations.isEmpty)
    },
  )

  def spec = suite("ViewRestrictionsByPropertyService")(equivalenceSuite, visibilitySuite)
}
