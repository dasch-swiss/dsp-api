/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.Dataset
import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepo
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Audience
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

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

  // ----- dataset-backed: the fold and the drill-down paging -----

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val anything = "http://www.knora.org/ontology/0001/anything#"
  private val hasText  = s"${anything}hasText"
  private val thing    = s"${anything}Thing"

  private val byProperty = ZIO.serviceWithZIO[ViewRestrictionsByPropertyService]

  /**
   * The repo's `OntologyRepo` is `null` on purpose: only `projectValueProperties` reads it, and that path
   * is covered by [[org.knora.webapi.slice.admin.repo.ViewRestrictionsByPropertyRepoSpec]]. Everything
   * exercised here goes to the triplestore, so wiring the ontology cache would add a layer stack without
   * adding coverage.
   */
  private val commonLayers = ZLayer.makeSome[Ref[Dataset], ViewRestrictionsByPropertyService](
    ViewRestrictionsByPropertyService.layer,
    ZLayer.fromFunction((ts: TriplestoreService) => ViewRestrictionsByPropertyRepo(ts, null)),
    StringFormatter.test,
    TriplestoreServiceInMemory.layer,
  )

  private def restrictedValue(iri: String) =
    s"""
       |<$iri> rdf:type kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "M knora-admin:ProjectMember" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /**
   * Three resources, each carrying **two** restricted values of one property, plus one world-readable
   * value on the first.
   *
   * Two values per resource is the whole point: it is the shape that makes "a page of value rows" and
   * "a total of distinct resources" disagree. With one value each the two units coincide and the paging
   * bug this fixture exists to catch is invisible.
   */
  private val pagingTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |<$hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/a> rdf:type <$thing> ;
       |  rdfs:label "A" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/a/v1>, <http://rdfh.ch/0001/a/v2>, <http://rdfh.ch/0001/a/open> .
       |
       |<http://rdfh.ch/0001/b> rdf:type <$thing> ;
       |  rdfs:label "B" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/b/v1>, <http://rdfh.ch/0001/b/v2> .
       |
       |<http://rdfh.ch/0001/c> rdf:type <$thing> ;
       |  rdfs:label "C" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/c/v1>, <http://rdfh.ch/0001/c/v2> .
       |
       |${restrictedValue("http://rdfh.ch/0001/a/v1")}
       |${restrictedValue("http://rdfh.ch/0001/a/v2")}
       |${restrictedValue("http://rdfh.ch/0001/b/v1")}
       |${restrictedValue("http://rdfh.ch/0001/b/v2")}
       |${restrictedValue("http://rdfh.ch/0001/c/v1")}
       |${restrictedValue("http://rdfh.ch/0001/c/v2")}
       |
       |<http://rdfh.ch/0001/a/open> rdf:type kb:TextValue ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false .
       |""".stripMargin

  /** One resource asserted as two classes in one hierarchy, carrying a single restricted value. */
  private val multiTypedTurtle: String =
    s"""
       |@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix kb:   <$kb> .
       |
       |<$thing> rdfs:subClassOf kb:Resource .
       |<${anything}SubThing> rdfs:subClassOf <$thing> .
       |<$hasText> rdfs:subPropertyOf kb:hasValue .
       |
       |<http://rdfh.ch/0001/multi> rdf:type <$thing>, <${anything}SubThing> ;
       |  rdfs:label "Multi" ;
       |  kb:attachedToProject <${projectIri.value}> ;
       |  kb:attachedToUser <http://rdfh.ch/users/someone> ;
       |  kb:hasPermissions "V knora-admin:UnknownUser" ;
       |  kb:isDeleted false ;
       |  <$hasText> <http://rdfh.ch/0001/multi/v1> .
       |
       |${restrictedValue("http://rdfh.ch/0001/multi/v1")}
       |""".stripMargin

  private def page(n: Int, size: Int) =
    byProperty(_.propertyItems(projectIri, hasText, ValueItemType.All, PageAndSize(n, size)))

  private val pagingSuite = suite("propertyItems paging")(
    test("a page is a page of resources, in the same unit the total counts") {
      // THE regression pin. Before the two-step window, the page query applied LIMIT/OFFSET to value rows
      // while the total counted distinct resources. With 3 resources x 2 restricted values, `totalItems`
      // was 3 and `totalPages` 2 at size 2, but the two pages consumed rows 1-4 — i.e. resources A and B
      // only. Resource C existed, was counted in the total, and was reachable by no page number at all.
      for {
        p1  <- page(1, 2)
        p2  <- page(2, 2)
        seen = (p1.data ++ p2.data).map(_.resourceIri)
      } yield assertTrue(
        p1.pagination.totalItems == 3,
        p1.pagination.totalPages == 2,
        // a page advertising size 2 returns 2 resources, not 2 rows' worth of them
        p1.data.size == 2,
        p2.data.size == 1,
        // every resource is reachable, and none is served twice
        seen.distinct.size == 3,
        seen.size == 3,
        seen.contains("http://rdfh.ch/0001/c"),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("a resource arrives whole: all of its restricted values on its own page, and none of the open ones") {
      // The other half of windowing by resource — a resource's values can no longer straddle a boundary
      // and come back twice, partial on each side.
      for {
        p1 <- page(1, 2)
        a   = p1.data.find(_.resourceIri == "http://rdfh.ch/0001/a")
      } yield assertTrue(
        a.map(_.values.size).contains(2),
        a.exists(_.values.map(_.valueIri).toSet == Set("http://rdfh.ch/0001/a/v1", "http://rdfh.ch/0001/a/v2")),
        // the world-readable value is not a restriction, so the drill-down omits it
        a.exists(!_.values.map(_.valueIri).contains("http://rdfh.ch/0001/a/open")),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("ordering is stable across pages, so paging cannot skip or repeat") {
      for {
        p1 <- page(1, 1)
        p2 <- page(2, 1)
        p3 <- page(3, 1)
      } yield assertTrue(
        p1.data.map(_.label) == Seq("A"),
        p2.data.map(_.label) == Seq("B"),
        p3.data.map(_.label) == Seq("C"),
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
    test("a page past the end is empty rather than an error") {
      for {
        p <- page(9, 25)
      } yield assertTrue(p.data.isEmpty, p.pagination.totalItems == 3)
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
  )

  private val foldSuite = suite("propertyValues")(
    test("counts values and derives the population from the same rows") {
      for {
        result <- byProperty(_.propertyValues(projectIri, hasText, ValueItemType.All))
      } yield assertTrue(
        result.property == hasText,
        // 6 restricted + 1 world-readable: the population includes literals that are in no restriction
        // bucket, which is what retires a separate population query.
        result.totalValues == 7,
        result.counts.anonymous.hidden == 6,
        result.counts.authenticated.hidden == 6,
        result.counts.projectMember.hidden == 0,
        result.counts.anonymous.restrictedView == 0,
        // and the denominator is in the same unit as the numerator
        result.counts.anonymous.total <= result.totalValues,
      )
    }.provide(commonLayers, datasetLayerFromTurtle(pagingTurtle)),
  )

  private val multiTypedSuite = suite("a resource asserted as several classes")(
    test("is one row carrying its value once, not one row per type") {
      // This report joins `?resource a ?resClass` without the most-specific-class filter the class report
      // needs, so a multi-typed resource comes back once per type. Left unhandled that repeats every value
      // under `values` and reports an arbitrary class.
      for {
        p <- page(1, 25)
      } yield assertTrue(
        p.pagination.totalItems == 1,
        p.data.size == 1,
        p.data.head.values.size == 1,
        p.data.head.values.head.valueIri == "http://rdfh.ch/0001/multi/v1",
        // deterministic rather than whichever binding the store returned first
        p.data.head.resourceClassIri == s"${anything}SubThing",
      )
    }.provide(commonLayers, datasetLayerFromTurtle(multiTypedTurtle)),
  )

  def spec = suite("ViewRestrictionsByPropertyService")(
    equivalenceSuite,
    visibilitySuite,
    pagingSuite,
    foldSuite,
    multiTypedSuite,
  )
}
