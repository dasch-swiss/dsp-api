/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import sttp.client4.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.json.ast.JsonCursor
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.api.v2.search.SearchEndpointsInputs.InputIri
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

object SearchE2EZSpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private val searchIncomingLinksSuite = suiteAll("Search Incoming Links endpoint") {
    val resourceIri = "http://rdfh.ch/0001/a-thing-picture"

    test("Successfully retrieve incoming links for a resource with offset=0") {
      val url          = uri"/v2/searchIncomingLinks/$resourceIri".withParam("offset", "0")
      val idCursor     = JsonCursor.field("@id").isString
      val targetCursor = JsonCursor
        .field("anything:hasThingPictureValue")
        .isObject
        .field("knora-api:linkValueHasTarget")
        .isObject
        .field("@id")
        .isString
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        id       <- ZIO.fromEither(response.get(idCursor))
        target   <- ZIO.fromEither(response.get(targetCursor))
      } yield assertTrue(
        id.value.contains("a-thing-with-picture"),
        target.value.contains("a-thing-picture"),
      )
    }

    test("Successfully retrieve incoming links for a resource with default offset") {
      val url          = uri"/v2/searchIncomingLinks/$resourceIri"
      val idCursor     = JsonCursor.field("@id").isString
      val targetCursor = JsonCursor
        .field("anything:hasThingPictureValue")
        .isObject
        .field("knora-api:linkValueHasTarget")
        .isObject
        .field("@id")
        .isString
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        id       <- ZIO.fromEither(response.get(idCursor))
        target   <- ZIO.fromEither(response.get(targetCursor))
      } yield assertTrue(
        id.value.contains("a-thing-with-picture"),
        target.value.contains("a-thing-picture"),
      )
    }

    test("Successfully retrieve incoming links with non-zero offset") {
      val url = uri"/v2/searchIncomingLinks/$resourceIri".withParam("offset", "1")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }

    test("Return empty result for resource with no incoming links") {
      val resourceIri = "http://rdfh.ch/0001/MAiNrOB1Q--rzAzdkqbHOw"
      val url         = uri"/v2/searchIncomingLinks/$resourceIri".withParam("offset", "0")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }
  }

  private val searchStillImageRepresentationsSuite = suiteAll("Search StillImageRepresentations endpoint") {
    val resourceIri = "http://rdfh.ch/0001/thing-with-pages"

    test("Successfully retrieve StillImageRepresentations for a resource with offset=0") {
      val url            = uri"/v2/searchStillImageRepresentations/$resourceIri".withParam("offset", "0")
      val idCursor       = JsonCursor.field("@id").isString
      val labelCursor    = JsonCursor.field("rdfs:label").isString
      val filenameCursor =
        JsonCursor.field("knora-api:hasStillImageFileValue").isObject.field("knora-api:fileValueHasFilename").isString

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        id       <- ZIO.fromEither(response.get(idCursor))
        label    <- ZIO.fromEither(response.get(labelCursor))
        filename <- ZIO.fromEither(response.get(filenameCursor))
      } yield assertTrue(
        id.value.contains("thing-page-1"),
        label.value.contains("page 1"),
        filename.value.contains("page1.jp2"),
      )
    }

    test("Successfully retrieve StillImageRepresentations for a resource with default offset") {
      val url            = uri"/v2/searchStillImageRepresentations/$resourceIri"
      val idCursor       = JsonCursor.field("@id").isString
      val labelCursor    = JsonCursor.field("rdfs:label").isString
      val filenameCursor =
        JsonCursor.field("knora-api:hasStillImageFileValue").isObject.field("knora-api:fileValueHasFilename").isString

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        id       <- ZIO.fromEither(response.get(idCursor))
        label    <- ZIO.fromEither(response.get(labelCursor))
        filename <- ZIO.fromEither(response.get(filenameCursor))
      } yield assertTrue(
        id.value.contains("thing-page-1"),
        label.value.contains("page 1"),
        filename.value.contains("page1.jp2"),
      )
    }

    test("Successfully retrieve StillImageRepresentations with non-zero offset") {
      val url = uri"/v2/searchStillImageRepresentations/$resourceIri".withParam("offset", "1")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }

    test("Return empty result for resource with no StillImageRepresentations") {
      val resourceIri = "http://rdfh.ch/0001/MAiNrOB1Q--rzAzdkqbHOw"
      val url         = uri"/v2/searchStillImageRepresentations/$resourceIri".withParam("offset", "0")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }
  }

  private val searchStillImageRepresentationsCountSuite = suiteAll("Search StillImageRepresentations Count endpoint") {
    test("Successfully retrieve count for a resource that has StillImageRepresentations") {
      val resourceIri = "http://rdfh.ch/0001/thing-with-pages"
      val url         = uri"/v2/searchStillImageRepresentationsCount/$resourceIri"
      val countCursor = JsonCursor.field("schema:numberOfItems").isNumber

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        count    <- ZIO.fromEither(response.get(countCursor))
      } yield assertTrue(count.value.intValue == 2)
    }

    test("Retrieve 0 count for a resource that has not any StillImageRepresentations") {
      val resourceIri = "http://rdfh.ch/0001/a-thing"
      val url         = uri"/v2/searchStillImageRepresentationsCount/$resourceIri"
      val countCursor = JsonCursor.field("schema:numberOfItems").isNumber

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        count    <- ZIO.fromEither(response.get(countCursor))
      } yield assertTrue(count.value.intValue == 0)
    }
  }

  private val searchIncomingRegionsSuite = suiteAll("Search Incoming Regions endpoint") {
    val resourceIri = InputIri.unsafeFrom("http://rdfh.ch/0001/a-thing-picture")

    test("Successfully retrieve incoming regions for a resource with offset=0") {
      val url           = uri"/v2/searchIncomingRegions/$resourceIri".withParam("offset", "0")
      val typeCursor    = JsonCursor.field("@type").isString
      val labelCursor   = JsonCursor.field("rdfs:label").isString
      val commentCursor = JsonCursor.field("knora-api:hasComment").isObject.field("knora-api:valueAsString").isString

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        t        <- ZIO.fromEither(response.get(typeCursor))
        l        <- ZIO.fromEither(response.get(labelCursor))
        c        <- ZIO.fromEither(response.get(commentCursor))
      } yield assertTrue(
        t.value.contains("knora-api:Region"),
        l.value.contains("A test region"),
        c.value.contains("A test label of the region"),
      )
    }

    test("Successfully retrieve incoming regions for a resource with the default offset") {
      val url           = uri"/v2/searchIncomingRegions/$resourceIri".withParam("offset", "0")
      val typeCursor    = JsonCursor.field("@type").isString
      val labelCursor   = JsonCursor.field("rdfs:label").isString
      val commentCursor = JsonCursor.field("knora-api:hasComment").isObject.field("knora-api:valueAsString").isString

      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
        t        <- ZIO.fromEither(response.get(typeCursor))
        l        <- ZIO.fromEither(response.get(labelCursor))
        c        <- ZIO.fromEither(response.get(commentCursor))
      } yield assertTrue(
        t.value.contains("knora-api:Region"),
        l.value.contains("A test region"),
        c.value.contains("A test label of the region"),
      )
    }

    test("Successfully retrieve incoming regions with non-zero offset") {
      val url = uri"/v2/searchIncomingRegions/$resourceIri".withParam("offset", "1")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }

    test("Return empty result for resource without incoming regions") {
      val resourceIri = "http://rdfh.ch/0001/MAiNrOB1Q--rzAzdkqbHOw"
      val url         = uri"/v2/searchIncomingLinks/$resourceIri".withParam("offset", "0")
      for {
        response <- TestApiClient.getJson[Json](url, rootUser).flatMap(_.assert200)
      } yield assertTrue(response == Json.Obj.empty)
    }
  }

  override def e2eSpec: Spec[env, Any] =
    suite("SearchIncomingLinksE2EZSpec")(
      searchIncomingLinksSuite,
      searchStillImageRepresentationsSuite,
      searchStillImageRepresentationsCountSuite,
      searchIncomingRegionsSuite,
    )
}
