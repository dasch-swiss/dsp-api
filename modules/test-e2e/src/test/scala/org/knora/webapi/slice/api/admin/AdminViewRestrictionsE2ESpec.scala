/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.PagedResponse
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.RestrictedResource
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ViewRestrictionsSummary
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
          // cumulative invariant: anonymous >= authenticated >= projectMember
          summary.totals.anonymous >= summary.totals.authenticated,
          summary.totals.authenticated >= summary.totals.projectMember,
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
          group    = summary.groups.head.id
          itemsUri =
            uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?groupBy=ResourceClass&group=$group&itemType=All&page=1&page-size=25"
          page <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          page.pagination.currentPage == 1,
          page.data.size <= 25,
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
