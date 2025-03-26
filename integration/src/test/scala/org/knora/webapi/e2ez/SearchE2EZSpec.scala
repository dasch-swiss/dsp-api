/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import zio.*
import zio.json.*
import zio.test.*
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import zio.json.ast.{Json, JsonCursor}

object SearchE2EZSpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private val searchIncomingLinksSuite = suiteAll("Search Incoming Links endpoint") {
    val resourceIri = InputIri.unsafeFrom("http://rdfh.ch/0001/a-thing-picture")

    test("Successfully retrieve incoming links for a resource with offset=0") {
      val url      = s"/v2/searchIncomingLinks/${urlEncode(resourceIri.value)}?offset=0"
      val idCursor = JsonCursor.field("@id").isString
      val targetCursor = JsonCursor
        .field("anything:hasThingPictureValue")
        .isObject
        .field("knora-api:linkValueHasTarget")
        .isObject
        .field("@id")
        .isString
      for {
        token       <- getRootToken
        responseStr <- sendGetRequestStringOrFail(url, Some(token))
        response    <- ZIO.fromEither(responseStr.fromJson[Json])
        id          <- ZIO.fromEither(response.get(idCursor))
        target      <- ZIO.fromEither(response.get(targetCursor))
      } yield assertTrue(
        id.value.contains("a-thing-with-picture"),
        target.value.contains("a-thing-picture"),
      )
    }

    test("Successfully retrieve incoming links for a resource with default offset") {
      val url      = s"/v2/searchIncomingLinks/${urlEncode(resourceIri.value)}"
      val idCursor = JsonCursor.field("@id").isString
      val targetCursor = JsonCursor
        .field("anything:hasThingPictureValue")
        .isObject
        .field("knora-api:linkValueHasTarget")
        .isObject
        .field("@id")
        .isString
      for {
        token       <- getRootToken
        responseStr <- sendGetRequestStringOrFail(url, Some(token))
        response    <- ZIO.fromEither(responseStr.fromJson[Json])
        id          <- ZIO.fromEither(response.get(idCursor))
        target      <- ZIO.fromEither(response.get(targetCursor))
      } yield assertTrue(
        id.value.contains("a-thing-with-picture"),
        target.value.contains("a-thing-picture"),
      )
    }

    test("Successfully retrieve incoming links with non-zero offset") {
      val url = s"/v2/searchIncomingLinks/${urlEncode(resourceIri.value)}?offset=1"
      for {
        token       <- getRootToken
        responseStr <- sendGetRequestStringOrFail(url, Some(token))
        response    <- ZIO.fromEither(responseStr.fromJson[Map[String, Json]])
      } yield assertTrue(
        response.isEmpty,
      )
    }
//
//
    test("Return empty result for resource with no incoming links") {
      val resourceIri = InputIri.unsafeFrom("http://rdfh.ch/0001/MAiNrOB1Q--rzAzdkqbHOw")
      val url         = s"/v2/searchIncomingLinks/${urlEncode(resourceIri.value)}?offset=0"
      for {
        token       <- getRootToken
        responseStr <- sendGetRequestStringOrFail(url, Some(token))
        response    <- ZIO.fromEither(responseStr.fromJson[Map[String, Json]])
      } yield assertTrue(
        response.isEmpty,
      )
    }
  }

  override def e2eSpec: Spec[env, Any] =
    suite("SearchIncomingLinksE2EZSpec")(
      searchIncomingLinksSuite,
    )
}
