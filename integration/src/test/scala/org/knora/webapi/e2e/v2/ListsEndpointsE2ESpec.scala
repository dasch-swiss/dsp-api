/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import spray.json.JsValue
import spray.json.JsonParser

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.util.FileUtil

import pekko.http.scaladsl.model.headers.Accept
import pekko.http.scaladsl.server.Directives.*

/**
 * End-to-end test specification for the lists endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class ListsEndpointsE2ESpec extends E2ESpec {

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(
      path = "test_data/project_data/images-demo-data.ttl",
      name = "http://www.knora.org/data/00FF/images",
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  "The lists v2 endpoint" should {

    "perform a request for a list in JSON-LD" in {
      val actual = getResponseAsJson(
        Get(s"$baseApiUrl/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}"),
      )
      val expected =
        JsonParser(FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesList.jsonld")))
      assert(actual == expected)
    }

    "perform a request for the anything treelist list in JSON-LD" in {
      val actual = getResponseAsJson(
        Get(s"$baseApiUrl/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "UTF-8")}"),
      )
      val expected: JsValue =
        JsonParser(FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/treelist.jsonld")))
      assert(actual == expected)
    }

    "perform a request for the anything othertreelist list in JSON-LD" in {
      val actual = getResponseAsJson(
        Get(s"$baseApiUrl/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/0001/otherTreeList", "UTF-8")}"),
      )
      val expected = JsonParser(
        FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/othertreelist.jsonld")),
      )
      assert(actual == expected)
    }

    "perform a request for a list in Turtle" in {
      val actualStr = getResponseAsString(
        Get(s"$baseApiUrl/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}")
          .addHeader(Accept(RdfMediaTypes.`text/turtle`)),
      )
      val expected: RdfModel =
        parseTurtle(FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesList.ttl")))
      val responseTurtle: RdfModel = parseTurtle(actualStr)
      assert(responseTurtle == expected)
    }

    "perform a request for a list in RDF/XML" in {
      val actualStr = getResponseAsString(
        Get(s"$baseApiUrl/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}")
          .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)),
      )
      val expectedAnswerRdfXml: RdfModel =
        parseRdfXml(FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesList.rdf")))
      val responseRdfXml: RdfModel = parseRdfXml(actualStr)
      assert(responseRdfXml == expectedAnswerRdfXml)
    }

    "perform a request for a node in JSON-LD" in {
      val actual = getResponseAsJson(
        Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}"),
      )
      val expected: JsValue =
        JsonParser(
          FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesListNode.jsonld")),
        )
      assert(actual == expected)
    }

    "perform a request for a treelist node in JSON-LD" in {
      val actual = getResponseAsJson(
        Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/0001/treeList01", "UTF-8")}"),
      )
      val expected: JsValue =
        JsonParser(
          FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/treelistnode.jsonld")),
        )
      assert(actual == expected)
    }

    "perform a request for a node in Turtle" in {
      val actual = getResponseAsString(
        Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}")
          .addHeader(Accept(RdfMediaTypes.`text/turtle`)),
      )
      val expectedAnswerTurtle: RdfModel =
        parseTurtle(
          FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesListNode.ttl")),
        )
      val responseTurtle: RdfModel = parseTurtle(actual)
      assert(responseTurtle == expectedAnswerTurtle)
    }

    "perform a request for a node in RDF/XML" in {
      val actual = getResponseAsString(
        Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}")
          .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)),
      )
      val expectedAnswerRdfXml: RdfModel =
        parseRdfXml(
          FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/listsR2RV2/imagesListNode.rdf")),
        )
      val responseRdfXml: RdfModel = parseRdfXml(actual)
      assert(responseRdfXml == expectedAnswerRdfXml)
    }
  }
}
