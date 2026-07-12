/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.test.assertTrue

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.api.v2.resources.ResourcesBatchRequest
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.ResponseOps.assert403
import org.knora.webapi.testservices.ResponseOps.assert404
import org.knora.webapi.testservices.TestApiClient

/**
 * E2E tests for `POST /v2/resources/batch` (fetch multiple resources by IRI via a JSON
 * body). The endpoint reuses the same fetch service and renderer as `GET /v2/resources`,
 * so soft-delete tombstone (200) and content-negotiation behaviour are inherited from the
 * reused service and covered by the GET specs (`ResourcesEndpointsGetResourcesE2ESpec`,
 * `ResourcesRouteV2E2ESpec`). These tests focus on the batch-specific behaviour:
 * request/response parity with the GET, de-duplication, the configurable cap, the
 * input-validation error cases, and the permission (403) / not-found (404) paths — the
 * latter two were not previously covered for a direct top-level request on either route.
 */
object ResourcesEndpointsPostBatchE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  private val user = SharedTestDataADM.superUser

  // 'Reise ins Heilige Land' (incunabula book) and a 'Testding' (anything Thing).
  private val bookIri  = ResourceIri.unsafeFrom("http://rdfh.ch/0803/2a6221216701")
  private val thingIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw")

  private def batch(iris: String*): ResourcesBatchRequest = ResourcesBatchRequest(iris.toList)

  private val parity = suite("parity with GET /v2/resources")(
    test("a single-IRI batch returns the same RDF as the GET for that IRI") {
      for {
        post <- TestApiClient
                  .postJsonReceiveString(uri"/v2/resources/batch", batch(bookIri.toString), user)
                  .flatMap(_.assert200)
        get <- TestApiClient.getJsonLd(uri"/v2/resources/$bookIri", user).flatMap(_.assert200)
      } yield assertTrue(RdfModel.fromJsonLD(post) == RdfModel.fromJsonLD(get))
    },
    test("a multi-IRI batch returns the same RDF as the multi-resource GET (order-independent)") {
      for {
        post <- TestApiClient
                  .postJsonReceiveString(uri"/v2/resources/batch", batch(bookIri.toString, thingIri.toString), user)
                  .flatMap(_.assert200)
        get <- TestApiClient.getJsonLd(uri"/v2/resources/$bookIri/$thingIri", user).flatMap(_.assert200)
      } yield assertTrue(RdfModel.fromJsonLD(post) == RdfModel.fromJsonLD(get))
    },
  )

  private val deduplication = suite("de-duplication")(
    test("duplicate IRIs are collapsed: a [A, A] batch equals a [A] batch") {
      for {
        withDup <- TestApiClient
                     .postJsonReceiveString(uri"/v2/resources/batch", batch(bookIri.toString, bookIri.toString), user)
                     .flatMap(_.assert200)
        single <- TestApiClient
                    .postJsonReceiveString(uri"/v2/resources/batch", batch(bookIri.toString), user)
                    .flatMap(_.assert200)
      } yield assertTrue(RdfModel.fromJsonLD(withDup) == RdfModel.fromJsonLD(single))
    },
  )

  private val errors = suite("validation and error cases")(
    test("an empty resourceIris list is rejected with 400 citing the minItems (>= 1) bound") {
      for {
        body <- TestApiClient.postJsonReceiveString(uri"/v2/resources/batch", batch(), user).flatMap(_.assert400)
      } yield assertTrue(
        body.contains("resourceIris"),
        body.contains("greater than or equal to 1"),
        body.contains("but got 0"),
      )
    },
    test("more IRIs than the configured cap are rejected with 400 citing the maxItems (100) bound, before any fetch") {
      val overCap = 101 // one past the default max-batch-size of 100
      val tooMany = batch(List.fill(overCap)("http://rdfh.ch/0001/does-not-need-to-exist")*)
      for {
        body <- TestApiClient.postJsonReceiveString(uri"/v2/resources/batch", tooMany, user).flatMap(_.assert400)
      } yield assertTrue(
        body.contains("resourceIris"),
        body.contains("less than or equal to 100"),
        body.contains(s"but got $overCap"),
      )
    },
    test("a syntactically invalid IRI is rejected with 400 identifying the offending IRI") {
      for {
        body <- TestApiClient
                  .postJsonReceiveString(uri"/v2/resources/batch", batch("not-a-valid-iri"), user)
                  .flatMap(_.assert400)
      } yield assertTrue(body.contains("not-a-valid-iri") && body.contains("is not a Knora resource IRI"))
    },
    test("a well-formed but non-existent IRI yields 404 listing the missing IRI") {
      val missing = "http://rdfh.ch/0803/000000000000"
      for {
        body <- TestApiClient
                  .postJsonReceiveString(uri"/v2/resources/batch", batch(missing), user)
                  .flatMap(_.assert404)
      } yield assertTrue(body.contains(missing) && body.contains("not found"))
    },
    test("a resource the user is not permitted to read yields 403 listing the forbidden IRI") {
      // "hidden thing" — project-member-only permissions; normalUser is not a member of 0001.
      val hiddenThing = "http://rdfh.ch/0001/IwMDbs0KQsaxSRUTl2cAIQ"
      for {
        body <- TestApiClient
                  .postJsonReceiveString(uri"/v2/resources/batch", batch(hiddenThing), SharedTestDataADM.normalUser)
                  .flatMap(_.assert403)
      } yield assertTrue(body.contains(hiddenThing) && body.contains("do not have permission"))
    },
  )

  override val e2eSpec = suite("ResourcesEndpointsPostBatchE2ESpec")(
    parity,
    deduplication,
    errors,
  )
}
