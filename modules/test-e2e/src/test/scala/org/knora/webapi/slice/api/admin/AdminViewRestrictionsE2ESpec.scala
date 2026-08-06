/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.ZIO
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemVisibility
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.RestrictedResource
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.RestrictionCounts
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.UnitCounts.anyRestriction
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ViewRestrictionsSummary
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class AdminViewRestrictionsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private val summaryUri =
    uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/summary?groupBy=ResourceClass&itemType=All"

  val e2eSpec = suite("The view-restrictions admin endpoint")(
    suite("summary")(
      test("returns 200 with a per-audience matrix for a system admin") {
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](summaryUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          summary.projectIri == anythingProjectIri.value,
          // cumulative invariant: access widens across audiences, so neither unit's total can grow
          summary.totals.anonymous.resources.total >= summary.totals.authenticated.resources.total,
          summary.totals.authenticated.resources.total >= summary.totals.projectMember.resources.total,
          summary.totals.anonymous.items.total >= summary.totals.authenticated.items.total,
          summary.totals.authenticated.items.total >= summary.totals.projectMember.items.total,
          // the fixture has restrictions of both kinds, so both buckets are exercised
          summary.totals.anonymous.items.hidden > 0,
          summary.totals.anonymous.items.restrictedView > 0,
          // per-group counts must sum to the reported totals, per unit
          summary.groups.map(_.counts.anonymous.resources.hidden).sum == summary.totals.anonymous.resources.hidden,
          summary.groups.map(_.counts.anonymous.items.hidden).sum == summary.totals.anonymous.items.hidden,
          summary.groups.map(_.counts.anonymous.items.restrictedView).sum ==
            summary.totals.anonymous.items.restrictedView,
          // in class mode every class of the project is reported, each with its resource population
          summary.groups.forall(_.totalResources.isDefined),
          // every listed class has resources — a class is reported because it exists, not because it is
          // restricted, so the population is the one number that is never zero here
          summary.groups.forall(g => g.totalResources.exists(_ > 0)),
          // THE INVARIANT: the resources unit is a real share of the population, on real project data. A
          // class can never have more restricted resources than it has resources — the "3 of 1" that the
          // old mixed count produced. The items unit is deliberately NOT bounded this way: a resource can
          // carry arbitrarily many restricted values.
          summary.groups.forall(g =>
            g.totalResources.exists(pop =>
              g.counts.anonymous.resources.total <= pop &&
                g.counts.authenticated.resources.total <= pop &&
                g.counts.projectMember.resources.total <= pop,
            ),
          ),
        )
      },
      // itemType=Resource asks only about whole resources, so the items unit must be empty across the board.
      // This is the filter under which the summary count and totalResources are directly comparable.
      test("reports only the resources unit under itemType=Resource") {
        val resourceUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/summary?groupBy=ResourceClass&itemType=Resource"
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](resourceUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          summary.groups.nonEmpty,
          summary.groups.forall(_.counts.anonymous.items == RestrictionCounts(0, 0)),
          summary.groups.forall(_.counts.authenticated.items == RestrictionCounts(0, 0)),
          summary.groups.forall(_.counts.projectMember.items == RestrictionCounts(0, 0)),
        )
      },
      // The population is a property of the class, so it must not move when the itemType filter narrows
      // which restrictions are reported. Same classes, same counts, whatever the filter.
      test("reports the same class populations regardless of the itemType filter") {
        val fileUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/summary?groupBy=ResourceClass&itemType=File"
        for {
          all   <- TestApiClient.getJson[ViewRestrictionsSummary](summaryUri, rootUser).flatMap(_.assert200)
          files <- TestApiClient.getJson[ViewRestrictionsSummary](fileUri, rootUser).flatMap(_.assert200)
        } yield {
          val allPop   = all.groups.map(g => g.id -> g.totalResources).toMap
          val filesPop = files.groups.map(g => g.id -> g.totalResources).toMap
          assertTrue(allPop.nonEmpty, allPop == filesPop)
        }
      },
      // The anything project has classes nobody restricted; those must still be listed with their counts,
      // which is what makes the populations sum to the project's whole resource count.
      test("lists classes that have no restrictions at all") {
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](summaryUri, rootUser).flatMap(_.assert200)
        } yield {
          val unrestricted = summary.groups.filter(g =>
            g.counts.anonymous.anyRestriction + g.counts.authenticated.anyRestriction +
              g.counts.projectMember.anyRestriction == 0,
          )
          assertTrue(
            unrestricted.nonEmpty,
            unrestricted.forall(_.totalResources.exists(_ > 0)),
          )
        }
      },
      // Property mode groups values across classes, so there is no resource population to report there.
      test("omits totalResources when grouping by property") {
        val propertyUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/summary?groupBy=Property&itemType=All"
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](propertyUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          summary.groups.nonEmpty,
          summary.groups.forall(_.totalResources.isEmpty),
        )
      },
      test("returns 200 for a project admin of the project") {
        TestApiClient
          .getJson[ViewRestrictionsSummary](summaryUri, anythingAdminUser)
          .flatMap(_.assert200)
          .as(assertCompletes)
      },
      test("returns 401 when no authentication is provided") {
        TestApiClient
          .getJson[ViewRestrictionsSummary](summaryUri)
          .map(response => assertTrue(response.code == StatusCode.Unauthorized))
      },
      test("returns 403 for a user who is neither system nor project admin") {
        TestApiClient
          .getJson[ViewRestrictionsSummary](summaryUri, anythingUser2)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("returns 403 for a project admin of a different project") {
        TestApiClient
          .getJson[ViewRestrictionsSummary](summaryUri, incunabulaProjectAdminUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("items (drill-down)")(
      test("returns a paged list of affected resources for a class the summary reports") {
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](summaryUri, rootUser).flatMap(_.assert200)
          // headOption, not head: a fixture change that opens up permissions should fail with a clear
          // assertion rather than a NoSuchElementException from an unrelated-looking line.
          group <- ZIO
                     .fromOption(summary.groups.headOption.map(_.id))
                     .orElseFail(new AssertionError("the summary reported no restricted groups to drill into"))
          itemsUri =
            uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=$group&itemType=All&page=1&page-size=25"
          page <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          page.pagination.currentPage == 1,
          page.data.size <= 25,
          // the drill-down must actually return the resources the summary counted
          page.pagination.totalItems > 0,
          page.data.nonEmpty,
          // every returned resource is either restricted itself or carries a restricted item
          page.data.forall(r =>
            r.resourceVisibility != ItemVisibility(Visibility.Visible, Visibility.Visible, Visibility.Visible) ||
              r.items.nonEmpty,
          ),
        )
      },
      // Paging is ordered in SPARQL (label, then IRI), so the same request must return the same rows.
      test("returns the same page on a repeated request (stable ordering)") {
        for {
          summary <- TestApiClient.getJson[ViewRestrictionsSummary](summaryUri, rootUser).flatMap(_.assert200)
          group   <- ZIO
                     .fromOption(summary.groups.headOption.map(_.id))
                     .orElseFail(new AssertionError("the summary reported no restricted groups to drill into"))
          itemsUri =
            uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=$group&itemType=All&page=1&page-size=5"
          first  <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
          second <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          first.data.map(_.resourceIri) == second.data.map(_.resourceIri),
          first.pagination.totalItems == second.pagination.totalItems,
        )
      },
      test("returns 403 for a non-admin user") {
        val itemsUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=x&itemType=All"
        TestApiClient
          .getJson[PagedResponse[RestrictedResource]](itemsUri, anythingUser2)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      // AC5 / W3: a malformed `group` value is a client error (400), not a 500 or an unhandled SPARQL error.
      test("returns 400 for a malformed group IRI (admin)") {
        val itemsUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=not%20an%20iri&itemType=All"
        TestApiClient
          .getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("returns 200 with an empty page for a well-formed but unmatched group IRI") {
        val unknownGroup = "http://www.knora.org/ontology/0001/anything#DoesNotExist"
        val itemsUri     =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=$unknownGroup&itemType=All"
        for {
          page <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(page.data.isEmpty, page.pagination.totalItems == 0)
      },
    ),
    suite("unknown project (AC5)")(
      // Deliberate: the shared admin authorization gate returns 403 (not 404) for a non-existent project so
      // it does not leak project existence to non-admins. Every admin route behaves this way — see
      // ViewRestrictionsRestService's authorization note.
      test("returns 403 for a well-formed but non-existent project IRI") {
        val unknownProject = "http://rdfh.ch/projects/FFFFFFFFFFFF"
        val uri            =
          uri"/admin/projects/iri/$unknownProject/view-restrictions/summary?groupBy=ResourceClass&itemType=All"
        TestApiClient
          .getJson[ViewRestrictionsSummary](uri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
  )
}
