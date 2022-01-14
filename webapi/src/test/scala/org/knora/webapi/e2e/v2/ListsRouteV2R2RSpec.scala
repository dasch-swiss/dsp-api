/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.routing.v2.ListsRouteV2
import org.knora.webapi.util.FileUtil
import spray.json.{JsValue, JsonParser}

import scala.concurrent.ExecutionContextExecutor

/**
 * End-to-end test specification for the lists endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class ListsRouteV2R2RSpec extends R2RSpec {

  override def testConfigSource: String =
    """
      |# akka.loglevel = "DEBUG"
      |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

  private val listsPath = new ListsRouteV2(routeData).knoraApiPath

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "lists")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The lists v2 endpoint" should {

    "perform a request for a list in JSON-LD" in {
      Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}") ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerJSONLD: JsValue =
          JsonParser(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesList.jsonld")))
        val responseJson: JsValue = JsonParser(responseAs[String])
        assert(responseJson == expectedAnswerJSONLD)
      }
    }

    "perform a request for the anything treelist list in JSON-LD" in {
      Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "UTF-8")}") ~> listsPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val expectedAnswerJSONLD: JsValue =
          JsonParser(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/treelist.jsonld")))
        val responseJson: JsValue = JsonParser(responseStr)
        assert(responseJson == expectedAnswerJSONLD)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "treelist",
              fileExtension = "json"
            ),
            text = responseStr
          )
        )
      }
    }

    "perform a request for the anything othertreelist list in JSON-LD" in {
      Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/0001/otherTreeList", "UTF-8")}") ~> listsPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val expectedAnswerJSONLD: JsValue =
          JsonParser(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/othertreelist.jsonld")))
        val responseJson: JsValue = JsonParser(responseStr)
        assert(responseJson == expectedAnswerJSONLD)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "othertreelist",
              fileExtension = "json"
            ),
            text = responseStr
          )
        )
      }
    }

    "perform a request for a list in Turtle" in {
      Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}")
        .addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerTurtle: RdfModel =
          parseTurtle(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesList.ttl")))
        val responseTurtle: RdfModel = parseTurtle(responseAs[String])
        assert(responseTurtle == expectedAnswerTurtle)
      }
    }

    "perform a request for a list in RDF/XML" in {
      Get(s"/v2/lists/${URLEncoder.encode("http://rdfh.ch/lists/00FF/73d0ec0302", "UTF-8")}")
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)) ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerRdfXml: RdfModel =
          parseRdfXml(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesList.rdf")))
        val responseRdfXml: RdfModel = parseRdfXml(responseAs[String])
        assert(responseRdfXml == expectedAnswerRdfXml)
      }
    }

    "perform a request for a node in JSON-LD" in {
      Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}") ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerJSONLD: JsValue =
          JsonParser(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesListNode.jsonld")))
        val responseJson: JsValue = JsonParser(responseAs[String])
        assert(responseJson == expectedAnswerJSONLD)
      }
    }

    "perform a request for a treelist node in JSON-LD" in {
      Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/0001/treeList01", "UTF-8")}") ~> listsPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val expectedAnswerJSONLD: JsValue =
          JsonParser(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/treelistnode.jsonld")))
        val responseJson: JsValue = JsonParser(responseStr)
        assert(responseJson == expectedAnswerJSONLD)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "listnode",
              fileExtension = "json"
            ),
            text = responseStr
          )
        )
      }
    }

    "perform a request for a node in Turtle" in {
      Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}")
        .addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerTurtle: RdfModel =
          parseTurtle(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesListNode.ttl")))
        val responseTurtle: RdfModel = parseTurtle(responseAs[String])
        assert(responseTurtle == expectedAnswerTurtle)
      }
    }

    "perform a request for a node in RDF/XML" in {
      Get(s"/v2/node/${URLEncoder.encode("http://rdfh.ch/lists/00FF/4348fb82f2", "UTF-8")}")
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)) ~> listsPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val expectedAnswerRdfXml: RdfModel =
          parseRdfXml(FileUtil.readTextFile(Paths.get("test_data/listsR2RV2/imagesListNode.rdf")))
        val responseRdfXml: RdfModel = parseRdfXml(responseAs[String])
        assert(responseRdfXml == expectedAnswerRdfXml)
      }
    }

  }
}
