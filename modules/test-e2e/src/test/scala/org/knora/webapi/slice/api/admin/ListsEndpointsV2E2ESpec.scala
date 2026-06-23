/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

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

object ListsEndpointsV2E2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfData, imagesRdfData, anythingRdfData)

  /** Which v2 route a fixture case exercises. */
  private enum Route(val segment: String) {
    case Lists extends Route("lists")
    case Node  extends Route("node")
  }

  /** A single `?allLanguages=true` fixture-comparison case. */
  private case class AllLangCase(label: String, route: Route, iri: String, fixture: String)

  /** Cases driving the `?allLanguages=true` fixture comparisons. */
  private val allLanguagesFixtureCases: Seq[AllLangCase] = Seq(
    AllLangCase(
      label = "return the imagesList in all-languages JSON-LD shape when allLanguages=true",
      route = Route.Lists,
      iri = "http://rdfh.ch/lists/00FF/73d0ec0302",
      fixture = "imagesListAllLanguages.jsonld",
    ),
    AllLangCase(
      label = "return the anything treelist in all-languages JSON-LD shape when allLanguages=true",
      route = Route.Lists,
      iri = "http://rdfh.ch/lists/0001/treeList",
      fixture = "treelistAllLanguages.jsonld",
    ),
    AllLangCase(
      label = "return the anything othertreelist in all-languages JSON-LD shape when allLanguages=true",
      route = Route.Lists,
      iri = "http://rdfh.ch/lists/0001/otherTreeList",
      fixture = "othertreelistAllLanguages.jsonld",
    ),
    AllLangCase(
      label = "return a treelist node in all-languages JSON-LD shape when allLanguages=true",
      route = Route.Node,
      iri = "http://rdfh.ch/lists/0001/treeList01",
      fixture = "treelistnodeAllLanguages.jsonld",
    ),
  )

  private val allLanguagesFixtureTests = allLanguagesFixtureCases.map { c =>
    test(c.label) {
      val listIri  = ListIri.unsafeFrom(c.iri)
      val expected = readAsJsonLd(Paths.get(s"test_data/generated_test_data/listsR2RV2/${c.fixture}"))
      val path     = uri"/v2/${c.route.segment}/$listIri?allLanguages=true"
      TestApiClient
        .getJsonLdDocument(path)
        .flatMap(_.assert200)
        .map(actual => assertTrue(actual == expected))
    }
  }

  private val explicitTests = Seq(
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
      val listIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/otherTreeList")
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

  // FIXME: Move to correct package. These are tests for /v2/lists
  val e2eSpec = suite("The lists v2 endpoint")(
    (explicitTests ++ allLanguagesFixtureTests)*,
  )
}
