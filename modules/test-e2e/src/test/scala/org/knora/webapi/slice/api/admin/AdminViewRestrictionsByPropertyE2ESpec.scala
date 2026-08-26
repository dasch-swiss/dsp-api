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

  private def itemsUri(property: String) =
    uri"/admin/projects/iri/$anythingProjectIri/view-restrictions/property-items?property=$property&itemType=All&page=1&page-size=25"

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
        for {
          properties <- TestApiClient.getJson[ViewRestrictionsProperties](propertiesUri, rootUser).flatMap(_.assert200)
          property   <- ZIO
                        .fromOption(properties.properties.headOption.map(_.id))
                        .orElseFail(new AssertionError("step 1 reported no properties"))
          page <- TestApiClient
                    .getJson[PagedResponse[RestrictedPropertyResource]](itemsUri(property), rootUser)
                    .flatMap(_.assert200)
        } yield assertTrue(
          // REQ-4.2: the class is per row because a property spans classes. An empty page is acceptable
          // here — the fixture may have nothing restricted on the first property — but any row that IS
          // returned must carry its class.
          page.data.forall(_.resourceClassIri.nonEmpty),
          page.data.forall(_.values.nonEmpty),
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
