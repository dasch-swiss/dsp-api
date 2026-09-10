/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.Crs
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient

/**
 * Tests the geolocation value at the HTTP boundary: a valid literal comes back with its parts derived,
 * and every rejection is a 400 whose message names the problem. The parser itself is covered
 * exhaustively by `GeolocationSpec`; the storage guarantees by `ValuesResponderV2Spec`.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class GeolocationValueE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private val aThingIri      = "http://rdfh.ch/0001/a-thing"
  private val hasGeolocation = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeolocation"

  private def createRequest(literal: String): String =
    s"""{
       |  "@id" : "$aThingIri",
       |  "@type" : "anything:Thing",
       |  "anything:hasGeolocation" : {
       |    "@type" : "knora-api:GeolocationValue",
       |    "knora-api:geolocationValueAsGeolocation" : "$literal"
       |  },
       |  "@context" : {
       |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
       |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
       |  }
       |}""".stripMargin

  private def create(literal: String) =
    TestApiClient.postJsonLdDocument(uri"/v2/values", createRequest(literal), anythingUser1)

  private def rejectionOf(literal: String) =
    create(literal).flatMap(_.assert400)

  /**
   * Creates a value and reads it back through the single-value endpoint, so that each test sees its own
   * value rather than whatever the previous test left on the resource.
   */
  private def savedGeolocation(literal: String) = for {
    created  <- create(literal).flatMap(_.assert200)
    valueIri <- ZIO.fromEither(created.body.getRequiredString(JsonLDKeywords.ID))
    valueUuid = valueIri.substring(valueIri.lastIndexOf('/') + 1)
    response <- TestApiClient
                  .getJsonLdDocument(uri"/v2/values/$aThingIri/$valueUuid", anythingUser1)
                  .flatMap(_.assert200)
    value <- ZIO.fromEither(valueOf(response.body))
  } yield value

  // The property is rendered as a bare object when it carries a single value and as an array otherwise.
  private def valueOf(resource: JsonLDObject): Either[String, JsonLDObject] =
    resource
      .getRequiredObject(hasGeolocation)
      .orElse(resource.getRequiredArray(hasGeolocation).flatMap { values =>
        values.value.collectFirst { case o: JsonLDObject => o }.toRight(s"No geolocation value in $resource")
      })

  override val e2eSpec: Spec[env, Any] = suite("GeolocationValueE2ESpec")(
    test("creates a geolocation value and returns its CRS, shape and coordinates as distinct fields") {
      val literal = s"<${Crs.Crs84.iri}> POINT(8.550 47.37)"
      for {
        value       <- savedGeolocation(literal)
        valueType   <- ZIO.fromEither(value.getRequiredString(JsonLDKeywords.TYPE))
        geolocation <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueAsGeolocation))
        crs         <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueHasCrs))
        shape       <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueHasShape))
        coordinates <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueHasCoordinates))
      } yield assertTrue(
        valueType == KA.GeolocationValue,
        geolocation == literal,
        crs == Crs.Crs84.iri,
        shape == "Point",
        coordinates == "8.550 47.37",
      )
    },
    test("stores an untagged literal with an explicit CRS84 prefix (REQ-5.8)") {
      for {
        value       <- savedGeolocation("POINT(7.44 46.95)")
        geolocation <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueAsGeolocation))
        crs         <- ZIO.fromEither(value.getRequiredString(KA.GeolocationValueHasCrs))
      } yield assertTrue(geolocation == s"<${Crs.Crs84.iri}> POINT(7.44 46.95)", crs == Crs.Crs84.iri)
    },
    // REQ-4.7: this is the whole reason valueHasString is the bare coordinates. The Fuseki text index
    // tokenizes on whitespace only, so storing "POINT(8.55 47.37)" would index "point(8.55" and "47.37)"
    // and this search would find nothing.
    test("finds the resource by a full-text search for a stored coordinate (REQ-4.7)") {
      val ordinate  = "46.9481"
      val notStored = "46.9482"
      for {
        _   <- create(s"<${Crs.Crs84.iri}> POINT(7.4474 $ordinate)").flatMap(_.assert200)
        hit <- TestApiClient.getJsonLdDocument(uri"/v2/search/$ordinate", anythingUser1).flatMap(_.assert200)
        // Negative control: without it, a response that happened to mention the resource for any other
        // reason would make the assertion above vacuous.
        miss <- TestApiClient.getJsonLd(uri"/v2/search/$notStored", anythingUser1).map(_.body.getOrElse(""))
      } yield assertTrue(hit.body.toString.contains(aThingIri), !miss.contains(aThingIri))
    },
    test("rejects EPSG:4326 with a 400 naming CRS84 (REQ-5.3)") {
      rejectionOf(s"<${Crs.RejectedEpsg4326}> POINT(8.55 47.37)")
        .map(message => assertTrue(message.contains(Crs.Crs84.iri)))
    },
    test("rejects a CRS outside the allowlist with a 400 (REQ-5.2)") {
      rejectionOf("<http://www.opengis.net/def/crs/EPSG/0/3857> POINT(8.55 47.37)")
        .map(message => assertTrue(message.contains(Crs.Lv95.iri)))
    },
    test("rejects an out-of-range coordinate with a 400 naming the ordinate and the CRS (REQ-5.4)") {
      rejectionOf(s"<${Crs.Crs84.iri}> POINT(8.55 947.37)")
        .map(message => assertTrue(message.contains("latitude"), message.contains(Crs.Crs84.label)))
    },
    test("rejects a geometry other than a POINT with a 400 (REQ-5.5)") {
      rejectionOf(s"<${Crs.Crs84.iri}> LINESTRING(8.55 47.37, 8.56 47.38)")
        .map(message => assertTrue(message.contains("LINESTRING")))
    },
    test("rejects an elevation with a 400 (REQ-5.6)") {
      rejectionOf(s"<${Crs.Crs84.iri}> POINT Z (8.55 47.37 400)")
        .map(message => assertTrue(message.contains("POINT Z")))
    },
    test("rejects a POINT with no coordinate pair with a 400 (REQ-5.7)") {
      rejectionOf(s"<${Crs.Crs84.iri}> POINT EMPTY").map(message => assertTrue(message.nonEmpty))
    },
    test("rejects an unparseable literal with a 400 (REQ-5.1)") {
      rejectionOf("8.55 47.37").map(message => assertTrue(message.nonEmpty))
    },
  )
}
