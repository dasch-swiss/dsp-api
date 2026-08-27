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
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.RestrictedPropertyResource
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.ViewRestrictionsProperties
import org.knora.webapi.slice.api.admin.ViewRestrictionsByPropertyEndpoints.ViewRestrictionsPropertyValues
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ViewRestrictionsClasses
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class AdminViewRestrictionsByPropertyE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  /** Step 1 takes no parameters: the property list cannot vary with a filter. */
  private val propertiesUri = uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/properties"

  private def valuesUri(property: String, itemType: String = "All") =
    uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/property-values?property=$property&itemType=$itemType"

  private def itemsUri(property: String, page: Int = 1, pageSize: Int = 25) =
    uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/property-items?property=$property&itemType=All&page=$page&page-size=$pageSize"

  /**
   * A property the fixture actually restricts, found by asking step 2 rather than assumed.
   *
   * Hard-coding one would couple these tests to the `anything` fixture's current contents; taking
   * `headOption` would make every drill-down assertion vacuous the moment that property happens to be
   * unrestricted. Fails loudly if nothing is restricted at all, since that would silently void the suite.
   */
  private val restrictedProperty: zio.RIO[TestApiClient, String] =
    for {
      properties <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
      // Short-circuits: once a restricted property is found the remaining step-2 requests are skipped,
      // so this is one or two round trips in practice rather than one per property in the ontology.
      candidate <- ZIO.foldLeft(properties.properties.map(_.id))(Option.empty[String]) {
                     case (found @ Some(_), _) => ZIO.succeed(found)
                     case (None, id)           =>
                       TestApiClient
                         .getJson[ViewRestrictionsPropertyValues](valuesUri(id), rootUser)
                         .flatMap(_.assert200)
                         .map(v => Option.when(v.counts.anonymous.total > 0)(id))
                   }
      found <- ZIO
                 .fromOption(candidate)
                 .orElseFail(new AssertionError("no property of the fixture carries a restriction"))
    } yield found

  val e2eSpec = suite("The property-grouped view-restrictions endpoints")(
    suite("properties (step 1)")(
      test("lists the project's value properties, including knora-base built-ins") {
        for {
          result <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          result.projectIri == anythingProjectIri.value,
          result.properties.nonEmpty,
          // knora-base is not among a project's own ontologies but its built-in file-value and comment
          // properties are used by project data, so omitting that fetch would silently drop those rows.
          result.properties.exists(_.ontology.contains("knora-base")),
          // and the project's own ontology is present too
          result.properties.exists(_.ontology.contains("anything")),
          // every row has a usable label
          result.properties.forall(_.label.nonEmpty),
        )
      },
      test("carries no counts — a property has one unit, so step 2 answers everything") {
        for {
          result <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(result.properties.nonEmpty)
      },
      test("returns 200 for a project admin") {
        TestApiClient
          .getJson[ViewRestrictionsProperties](propertiesUri, anythingAdminUser)
          .flatMap(_.assert200)
          .as(assertCompletes)
      },
      test("returns 401 when unauthenticated") {
        TestApiClient
          .getJson[ViewRestrictionsProperties](propertiesUri)
          .map(r => assertTrue(r.code == StatusCode.Unauthorized))
      },
      test("returns 403 for a user who is neither system nor project admin") {
        TestApiClient
          .getJson[ViewRestrictionsProperties](propertiesUri, anythingUser2)
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),
    suite("property-values (step 2)")(
      test("counts one property, with totalValues as a denominator in the same unit") {
        for {
          properties <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
          // headOption, not head: a fixture change should fail with a clear assertion rather than a
          // NoSuchElementException from an unrelated-looking line.
          property <- ZIO
                        .fromOption(properties.properties.headOption.map(_.id))
                        .orElseFail(new AssertionError("step 1 reported no properties to count"))
          result <- TestApiClient
                      .getJson[ViewRestrictionsPropertyValues](valuesUri(property), rootUser)
                      .flatMap(_.assert200)
        } yield assertTrue(
          result.projectIri == anythingProjectIri.value,
          result.property == property,
          // THE invariant this report's denominator rests on: the counts are values of this property and so
          // is totalValues, which makes "n of N" a sound statement rather than a mixed-unit ratio.
          result.counts.anonymous.total <= result.totalValues,
          result.counts.authenticated.total <= result.totalValues,
          result.counts.projectMember.total <= result.totalValues,
          // access widens across the audiences, so counts can only shrink
          result.counts.anonymous.total >= result.counts.authenticated.total,
          result.counts.authenticated.total >= result.counts.projectMember.total,
        )
      },
      test("itemType narrows the counts") {
        for {
          properties <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
          property   <- ZIO
                        .fromOption(properties.properties.headOption.map(_.id))
                        .orElseFail(new AssertionError("step 1 reported no properties"))
          all <- TestApiClient
                   .getJson[ViewRestrictionsPropertyValues](valuesUri(property, "All"), rootUser)
                   .flatMap(_.assert200)
          comments <- TestApiClient
                        .getJson[ViewRestrictionsPropertyValues](valuesUri(property, "Comment"), rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          comments.itemType == ValueItemType.Comment,
          // a narrowed filter can only ever count a subset of All
          comments.counts.anonymous.total <= all.counts.anonymous.total,
          comments.totalValues <= all.totalValues,
        )
      },
      test("returns 400 for a malformed property IRI") {
        TestApiClient
          .getJson[ViewRestrictionsPropertyValues](valuesUri("not an iri"), rootUser)
          .map(r => assertTrue(r.code == StatusCode.BadRequest))
      },
      test("returns zero counts for a well-formed but unused property") {
        // Not an error: a property nothing uses is a legitimate all-zero row, which is why step 1 lists
        // every property rather than only those with data.
        for {
          result <- TestApiClient
                      .getJson[ViewRestrictionsPropertyValues](valuesUri("http://example.org/nope"), rootUser)
                      .flatMap(_.assert200)
        } yield assertTrue(
          result.totalValues == 0,
          result.counts.anonymous.total == 0,
          result.counts.projectMember.total == 0,
        )
      },
      test("returns 403 for a non-admin") {
        TestApiClient
          .getJson[ViewRestrictionsPropertyValues](valuesUri("http://example.org/nope"), anythingUser2)
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),
    suite("property-items (drill-down)")(
      test("returns affected resources, each reporting its own class") {
        // Driven from a property step 2 says is actually restricted, rather than from `headOption`. A
        // `forall` over the first property's page passes vacuously whenever that page is empty, which
        // makes the assertion survive a fixture with no restrictions at all — i.e. it tests nothing.
        for {
          property <- restrictedProperty
          page     <- TestApiClient
                    .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri(property), rootUser)
                    .flatMap(_.assert200)
        } yield assertTrue(
          // Not vacuous: step 2 reported restrictions here, so the drill-down must list them.
          page.data.nonEmpty,
          page.pagination.totalItems > 0,
          // REQ-4.2: the class is per row, because a property spans classes.
          page.data.forall(_.resourceClassIri.nonEmpty),
          page.data.forall(_.values.nonEmpty),
        )
      },
      test("pages resources, not value rows: every resource is reachable exactly once") {
        // The unit invariant. `totalItems` counts distinct resources, so the pages must too — otherwise a
        // property with several restricted values per resource leaves its tail unreachable. Walking every
        // page of size 1 and comparing against the total is the end-to-end form of that check.
        for {
          property <- restrictedProperty
          first    <- TestApiClient
                     .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri(property, pageSize = 1), rootUser)
                     .flatMap(_.assert200)
          total = first.pagination.totalItems
          // Bounded so a large fixture cannot turn this into a slow crawl; the assertion below accounts
          // for the cap rather than silently checking a prefix.
          walked = math.min(total, 10)
          pages <-
            ZIO.foreach(1 to walked) { n =>
              TestApiClient
                .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri(property, n, pageSize = 1), rootUser)
                .flatMap(_.assert200)
            }
          iris = pages.flatMap(_.data.map(_.resourceIri))
        } yield assertTrue(
          total > 0,
          // one resource per page of size 1 — not one value row
          pages.forall(_.data.size == 1),
          // and no resource served twice across the walk
          iris.distinct.size == walked,
        )
      },
      test("returns 400 for a malformed property IRI") {
        TestApiClient
          .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri("not an iri"), rootUser)
          .map(r => assertTrue(r.code == StatusCode.BadRequest))
      },
      test("returns 403 for a non-admin") {
        TestApiClient
          .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri("http://example.org/nope"), anythingUser2)
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),
    suite("the class report is untouched (REQ-6.1)")(
      test("the class endpoints still answer with their own shapes") {
        // The property report is additive. If this fails, the separation was not as clean as claimed.
        val classesUri = uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/classes"
        for {
          result <- TestApiClient.getJson[ViewRestrictionsClasses](classesUri, rootUser).flatMap(_.assert200)
        } yield assertTrue(
          result.projectIri == anythingProjectIri.value,
          result.classes.nonEmpty,
          // still the resource unit, with its own denominator — not replaced by the property one
          result.classes.forall(_.totalResources >= 0),
        )
      },
    ),
  )
}
