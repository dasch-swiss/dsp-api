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
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ViewRestrictionsClasses
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ViewRestrictionsValues
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.Visibility
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class AdminViewRestrictionsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  /** Step 1 takes no parameters at all — resource-level counts are never filtered. */
  private val classesUri = uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/classes"

  private val thingClassIri = "http://www.knora.org/ontology/0001/anything#Thing"

  /** Step 2 is always scoped to exactly one class. */
  private def valuesUri(resourceClass: String, itemType: String = "All") =
    uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/values?resourceClass=$resourceClass&itemType=$itemType"

  /**
   * The first class reporting a resource-level restriction for any audience.
   *
   * The drill-down lists restrictions, so a test that opens a class with none gets an empty page and
   * fails for a reason that has nothing to do with the drill-down.
   */
  private def restrictedClass(classes: ViewRestrictionsClasses): Option[String] =
    classes.classes
      .find(c => c.counts.anonymous.total > 0 || c.counts.authenticated.total > 0 || c.counts.projectMember.total > 0)
      .map(_.id)

  val e2eSpec = suite("The view-restrictions admin endpoint")(
    suite("classes (step 1)")(
      test("returns every class with its population and resource-level counts, in one request") {
        for {
          result <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          result.projectIri == anythingProjectIri.value,
          result.classes.nonEmpty,
          // Every class is a row because it exists, not because it is restricted, so the population is the
          // one figure that is never zero on this fixture.
          result.classes.forall(_.totalResources > 0),
          // Cumulative invariant: access widens across the audiences, so counts can only shrink.
          result.classes.forall(c => c.counts.anonymous.total >= c.counts.authenticated.total),
          result.classes.forall(c => c.counts.authenticated.total >= c.counts.projectMember.total),
          // THE INVARIANT the unit split exists to protect: these counts are whole resources, so they can
          // never exceed the class's resource population. The old mixed count could report "3 of 1".
          result.classes.forall(c => c.counts.anonymous.total <= c.totalResources),
          result.classes.forall(c => c.counts.authenticated.total <= c.totalResources),
          result.classes.forall(c => c.counts.projectMember.total <= c.totalResources),
        )
      },
      test("takes no itemType filter — resource counts are never filtered") {
        // An unknown query parameter is ignored by tapir rather than rejected, so this asserts the response
        // is identical with and without it: proof the filter cannot affect step 1.
        for {
          plain    <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
          filtered <- TestApiClient
                        .getJson[ViewRestrictionsClasses](uri"$classesUri?itemType=Comment", rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(plain == filtered)
      },
      test("returns 200 for a project admin of the project") {
        TestApiClient
          .getJson[ViewRestrictionsClasses](classesUri, anythingAdminUser)
          .flatMap(_.assert200)
          .as(assertCompletes)
      },
      test("returns 401 when no authentication is provided") {
        TestApiClient
          .getJson[ViewRestrictionsClasses](classesUri)
          .map(response => assertTrue(response.code == StatusCode.Unauthorized))
      },
      test("returns 403 for a user who is neither system nor project admin") {
        TestApiClient
          .getJson[ViewRestrictionsClasses](classesUri, anythingUser2)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("values (step 2)")(
      test("returns value-level counts for one class named by the step-1 response") {
        for {
          classes <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
          // headOption, not head: a fixture change should fail with a clear assertion rather than a
          // NoSuchElementException from an unrelated-looking line.
          clazz <- ZIO
                     .fromOption(classes.classes.headOption.map(_.id))
                     .orElseFail(new AssertionError("step 1 reported no classes to count values for"))
          result <- TestApiClient.getJson[ViewRestrictionsValues](valuesUri(clazz), rootUser).flatMap(_.assert200)
        } yield assertTrue(
          result.projectIri == anythingProjectIri.value,
          result.resourceClass == clazz,
          // Same cumulative invariant, in the value unit. Deliberately NOT bounded by totalResources: one
          // resource can carry arbitrarily many restricted values.
          result.counts.anonymous.total >= result.counts.authenticated.total,
          result.counts.authenticated.total >= result.counts.projectMember.total,
        )
      },
      test("itemType narrows the counts") {
        for {
          all <- TestApiClient
                   .getJson[ViewRestrictionsValues](valuesUri(thingClassIri, "All"), rootUser)
                   .flatMap(_.assert200)
          comments <- TestApiClient
                        .getJson[ViewRestrictionsValues](valuesUri(thingClassIri, "Comment"), rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          comments.itemType == ValueItemType.Comment,
          // A narrowed filter can only ever count a subset of All.
          comments.counts.anonymous.total <= all.counts.anonymous.total,
        )
      },
      test("returns 400 for a malformed resourceClass IRI") {
        TestApiClient
          .getJson[ViewRestrictionsValues](valuesUri("not an iri"), rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("returns zero counts for a well-formed but unmatched resourceClass") {
        // Not an error: a class with nothing restricted is a legitimate all-zero row, and the frontend
        // renders it as such rather than as a failure.
        for {
          result <- TestApiClient
                      .getJson[ViewRestrictionsValues](valuesUri("http://example.org/NoSuchClass"), rootUser)
                      .flatMap(_.assert200)
        } yield assertTrue(
          result.counts.anonymous.total == 0,
          result.counts.authenticated.total == 0,
          result.counts.projectMember.total == 0,
        )
      },
      test("returns 403 for a non-admin user") {
        TestApiClient
          .getJson[ViewRestrictionsValues](valuesUri(thingClassIri), anythingUser2)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
    suite("items (drill-down)")(
      test("returns a paged list of affected resources for a class step 1 reports") {
        for {
          classes <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
          // A class with something actually restricted, not simply the first one. /classes reports EVERY
          // class ordered by label, so the first is usually one with nothing restricted and its drill-down
          // is legitimately empty. The old /summary happened to order most-restricted-first, which made
          // `headOption` work by accident; that ordering moved to the frontend when orderKey was deleted.
          group <- ZIO
                     .fromOption(restrictedClass(classes))
                     .orElseFail(new AssertionError("no class in the fixture has a resource-level restriction"))
          itemsUri =
            uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?resourceClass=$group&itemType=All&page=1&page-size=25"
          page <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          page.pagination.currentPage == 1,
          page.data.size <= 25,
          // the drill-down must actually return the resources step 1 counted
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
          classes <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
          // Also a restricted class: comparing two empty pages passes without exercising ordering at all.
          group <- ZIO
                     .fromOption(restrictedClass(classes))
                     .orElseFail(new AssertionError("no class in the fixture has a resource-level restriction"))
          itemsUri =
            uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?resourceClass=$group&itemType=All&page=1&page-size=5"
          first  <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
          second <- TestApiClient.getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          first.data.nonEmpty,
          first.data.map(_.resourceIri) == second.data.map(_.resourceIri),
          first.pagination.totalItems == second.pagination.totalItems,
        )
      },
      test("returns 403 for a non-admin user") {
        val itemsUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?resourceClass=x&itemType=All"
        TestApiClient
          .getJson[PagedResponse[RestrictedResource]](itemsUri, anythingUser2)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      // AC5 / W3: a malformed `resourceClass` value is a client error (400), not a 500 or an unhandled SPARQL error.
      test("returns 400 for a malformed resourceClass IRI (admin)") {
        val itemsUri =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?resourceClass=not%20an%20iri&itemType=All"
        TestApiClient
          .getJson[PagedResponse[RestrictedResource]](itemsUri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("returns 200 with an empty page for a well-formed but unmatched resourceClass IRI") {
        val unknownGroup = "http://www.knora.org/ontology/0001/anything#DoesNotExist"
        val itemsUri     =
          uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/items?resourceClass=$unknownGroup&itemType=All"
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
          uri"/admin/projects/iri/$unknownProject/view-restrictions/classes"
        TestApiClient
          .getJson[ViewRestrictionsClasses](uri, rootUser)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
  )
}
