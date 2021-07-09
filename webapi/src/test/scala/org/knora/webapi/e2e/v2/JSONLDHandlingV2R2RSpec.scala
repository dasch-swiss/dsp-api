/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.nio.file.Paths
import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.routing.v2.ResourcesRouteV2
import spray.json._

import scala.concurrent.ExecutionContextExecutor

/**
  * End-to-end specification for the handling of JSONLD documents.
  */
class JSONLDHandlingV2R2RSpec extends R2RSpec {

  override def testConfigSource: String =
    """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

  private val resourcesPath = new ResourcesRouteV2(routeData).knoraApiPath

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
  private val writeTestDataFiles = false

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
  )

  "The JSON-LD processor" should {

    "produce the expected JSON-LD context object (on the server side)" in {

      Get("/v2/resources/" + URLEncoder.encode("http://rdfh.ch/resources/O_wI0t9iQYy4cUG6ex_UAg", "UTF-8")) ~> resourcesPath ~> check {

        assert(status == StatusCodes.OK, response.toString)
        val responseAsString = responseAs[String]
        val receivedJson: JsObject = JsonParser(responseAsString).asJsObject

        val expectedJson: JsObject = JsonParser(
          readOrWriteTextFile(responseAsString,
                              Paths.get("test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
                              writeFile = writeTestDataFiles)
        ).asJsObject

        assert(receivedJson.fields("@context") == expectedJson.fields("@context"), "@context incorrect")

      }

    }

    "expand prefixes (on the client side)" in {

      // Reads the JSON-LD with prefixes and context object, since this reads the already existing
      // NarrenschiffFirstPage.jsonld,  writeFile should always be false
      val jsonldWithPrefixes =
        readOrWriteTextFile("", Paths.get("test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"), writeFile = false)

      // expand JSON-LD with JSON-LD processor
      val jsonldParsedExpanded: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonldWithPrefixes)

      // expected result after expansion
      val expectedJsonldExpandedParsed = JsonLDUtil.parseJsonLD(
        readOrWriteTextFile(jsonldParsedExpanded.body.toString,
                            Paths.get("test_data/resourcesR2RV2/NarrenschiffFirstPageExpanded.jsonld"),
                            writeFile = writeTestDataFiles)
      )

      compareParsedJSONLDForResourcesResponse(expectedResponse = expectedJsonldExpandedParsed,
                                              receivedResponse = jsonldParsedExpanded)

    }

  }

}
