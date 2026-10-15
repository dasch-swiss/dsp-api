/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Audience
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility

/**
 * Tests for [[ViewRestrictionsAudience]]. Pure: the properties under test belong to the permission model,
 * not to any project's data.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsAudienceSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  private val realCreators = Seq(
    "http://rdfh.ch/users/creator",
    "http://rdfh.ch/users/root",
    "http://rdfh.ch/users/someone-else",
    "http://rdfh.ch/users/some-real-person",
  )
  private val realCreator = realCreators.last

  /**
   * Literals spanning the cases the report distinguishes, including the ones that make the argument
   * non-obvious: `CR knora-admin:Creator` grants the creator full rights and everyone else nothing, so it
   * is exactly where a placeholder creator could have diverged from a real one.
   */
  private val literals = List(
    "V knora-admin:UnknownUser",
    "M knora-admin:ProjectMember",
    "V knora-admin:KnownUser",
    "V knora-admin:KnownUser|M knora-admin:ProjectMember",
    "RV knora-admin:UnknownUser|V knora-admin:KnownUser",
    "CR knora-admin:Creator",
    "CR knora-admin:Creator|V knora-admin:UnknownUser",
    "CR knora-admin:Creator|V knora-admin:ProjectMember",
  )

  private def visibilityWith(creator: String, literal: String, audience: Audience): Visibility =
    ViewRestrictionsAudience.visibilityOf(
      PermissionUtilADM.getUserPermissionADM(
        entityCreator = creator,
        entityProject = projectIri.value,
        entityPermissionLiteral = literal,
        requestingUser = ViewRestrictionsAudience.audienceUser(audience, projectIri),
      ),
    )

  private val visibilityOfSuite = suite("visibilityOf")(
    test("no permission at all is Hidden") {
      assertTrue(ViewRestrictionsAudience.visibilityOf(None) == Visibility.Hidden)
    },
    test("maps RestrictedView (code 1) to RestrictedView for every item type") {
      assertTrue(
        ViewRestrictionsAudience.visibilityOf(Some(Permission.ObjectAccess.RestrictedView)) ==
          Visibility.RestrictedView,
      )
    },
    test("maps View and higher (code >= 2) to Visible") {
      val visibilities = Seq(
        Permission.ObjectAccess.View,
        Permission.ObjectAccess.Modify,
        Permission.ObjectAccess.ChangeRights,
      ).map(p => ViewRestrictionsAudience.visibilityOf(Some(p)))
      assertTrue(visibilities.forall(_ == Visibility.Visible))
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
      val rv = visibilityWith(realCreator, "RV knora-admin:UnknownUser|V knora-admin:KnownUser", Audience.Anonymous)
      assertTrue(rv == Visibility.RestrictedView)
    },
    test("access widens across the audiences, never narrows") {
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

  private val audienceUserSuite = suite("audienceUser")(
    test("the anonymous audience user is not an admin (would otherwise short-circuit to max permission)") {
      val user = ViewRestrictionsAudience.audienceUser(Audience.Anonymous, projectIri)
      assertTrue(user.isAnonymousUser, !user.isSystemAdmin, !user.isProjectAdmin(projectIri))
    },
    test("the project-member audience user is a member but not an admin of the project") {
      val user = ViewRestrictionsAudience.audienceUser(Audience.ProjectMember, projectIri)
      assertTrue(user.isProjectMember(projectIri), !user.isProjectAdmin(projectIri), !user.isSystemAdmin)
    },
    test("no audience user is ever the creator, which is why the placeholder is sound") {
      val ids = Audience.ordered.map(a => ViewRestrictionsAudience.audienceUser(a, projectIri).id)
      assertTrue(
        ids.distinct.nonEmpty,
        ids.forall(id => !realCreators.contains(id)),
        !ids.contains(ViewRestrictionsAudience.syntheticCreatorPlaceholder),
      )
    },
  )

  private val placeholderSuite = suite("syntheticCreatorPlaceholder")(
    test("classifying a bare literal equals classifying a real object, for every audience and creator") {
      val mismatches = for {
        literal        <- literals
        audience       <- Audience.ordered
        withPlaceholder = visibilityWith(ViewRestrictionsAudience.syntheticCreatorPlaceholder, literal, audience)
        creator        <- realCreators
        withReal        = visibilityWith(creator, literal, audience)
        if withPlaceholder != withReal
      } yield s"$literal / $audience / $creator: placeholder=$withPlaceholder real=$withReal"
      assertTrue(mismatches.isEmpty)
    },
    test("a Creator-only literal grants nothing to any audience") {
      val results = Audience.ordered.map(a => visibilityWith(realCreator, "CR knora-admin:Creator", a))
      assertTrue(results.forall(_ == Visibility.Hidden))
    },
    test("classify agrees with resolving each literal with the placeholder creator") {
      val classified = ViewRestrictionsAudience.classify(literals.toSet, projectIri)
      val expected   = (for {
        literal  <- literals
        audience <- Audience.ordered
      } yield (literal, audience) -> visibilityWith(
        ViewRestrictionsAudience.syntheticCreatorPlaceholder,
        literal,
        audience,
      )).toMap
      assertTrue(classified == expected)
    },
  )

  override def spec: Spec[TestEnvironment & zio.Scope, Any] =
    suite("ViewRestrictionsAudience")(visibilityOfSuite, audienceUserSuite, placeholderSuite)
}
