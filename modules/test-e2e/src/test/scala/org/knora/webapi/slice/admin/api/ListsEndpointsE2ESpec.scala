/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.*
import zio.test.*

import java.nio.file.Paths

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.testservices.RequestsUpdates.addAcceptHeaderRdfXml
import org.knora.webapi.testservices.RequestsUpdates.addAcceptHeaderTurtle
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.FileUtil.*

object ListsEndpointsE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfData, imagesRdfData, anythingRdfData)

  // FIXME: Move to correct package. These are tests for /v2/lists
  val e2eSpec = suite("The lists v2 endpoint")(
    test("perform a request for a list in JSON-LD") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/73d0ec0302")
      val expected = readAsJsonLd(Paths.get("test_data/generated_test_data/listsR2RV2/imagesList.jsonld"))
      TestApiClient
        .getJsonLdDocument(uri"/v2/lists/$listIri")
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for the anything treelist list in JSON-LD") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")
      val expected = readAsJsonLd(Paths.get("test_data/generated_test_data/listsR2RV2/treelist.jsonld"))
      TestApiClient
        .getJsonLdDocument(uri"/v2/lists/$listIri")
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for the anything othertreelist list in JSON-LD") {
      val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/otherTreeList")
      val expected =
        readAsJsonLd(Paths.get("test_data/generated_test_data/listsR2RV2/othertreelist.jsonld"))
      TestApiClient
        .getJsonLdDocument(uri"/v2/lists/$listIri")
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a list in Turtle") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/73d0ec0302")
      val expected = readAsTurtle(Paths.get("test_data/generated_test_data/listsR2RV2/imagesList.ttl"))
      TestApiClient
        .getAsString(uri"/v2/lists/$listIri", addAcceptHeaderTurtle)
        .flatMap(_.assert200)
        .mapAttempt(RdfModel.fromTurtle)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a list in RDF/XML") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/73d0ec0302")
      val expected = readAsRdfXml(Paths.get("test_data/generated_test_data/listsR2RV2/imagesList.rdf"))
      TestApiClient
        .getAsString(uri"/v2/lists/$listIri", addAcceptHeaderRdfXml)
        .flatMap(_.assert200)
        .mapAttempt(RdfModel.fromRdfXml)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a node in JSON-LD") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/4348fb82f2")
      val expected = readAsJsonLd(Paths.get("test_data/generated_test_data/listsR2RV2/imagesListNode.jsonld"))
      TestApiClient
        .getJsonLdDocument(uri"/v2/node/$listIri")
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a treelist node in JSON-LD") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
      val expected = readAsJsonLd(Paths.get("test_data/generated_test_data/listsR2RV2/treelistnode.jsonld"))
      TestApiClient
        .getJsonLdDocument(uri"/v2/node/$listIri")
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a node in Turtle") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/4348fb82f2")
      val expected = readAsTurtle(Paths.get("test_data/generated_test_data/listsR2RV2/imagesListNode.ttl"))
      TestApiClient
        .getAsString(uri"/v2/node/$listIri", addAcceptHeaderTurtle)
        .flatMap(_.assert200)
        .mapAttempt(RdfModel.fromTurtle)
        .map(actual => assertTrue(actual == expected))
    },
    test("perform a request for a node in RDF/XML") {
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/4348fb82f2")
      val expected = readAsRdfXml(Paths.get("test_data/generated_test_data/listsR2RV2/imagesListNode.rdf"))
      TestApiClient
        .getAsString(uri"/v2/node/$listIri", addAcceptHeaderRdfXml)
        .flatMap(_.assert200)
        .mapAttempt(RdfModel.fromRdfXml)
        .map(actual => assertTrue(actual == expected))
    },
  )
}
