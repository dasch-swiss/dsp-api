/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.io.File
import java.net.URLEncoder
import java.util

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{FileUtil, JavaUtil}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * End-to-end specification for the handling of JSONLD documents.
  */
class JSONLDHandlingV2R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val resourcesPath = ResourcesRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The JSONLD processor" should {

        "expand prefixes (on the client side)" in {

            // JSONLD with prefixes and context object
            val jsonldWithPrefixes = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"))

            // expand JSONLD with JSONLD processor
            val jsonldExpandedAsScala = JavaUtil.deepJavatoScala(JsonLdProcessor.compact(JsonUtils.fromString(jsonldWithPrefixes), new util.HashMap[String, String](), new JsonLdOptions())).asInstanceOf[Map[String, Any]]

            // expected result after expansion
            val expectedJsonldExpandedAsScala = JavaUtil.deepJavatoScala(JsonUtils.fromString(FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/NarrenschiffFirstPageExpanded.jsonld")))).asInstanceOf[Map[String, Any]]

            compareParsedJSONLD(expectedResponseAsScala = expectedJsonldExpandedAsScala, receivedResponseAsScala = jsonldExpandedAsScala)

        }

        "produce the expected JSONLD context object (on the server side)" in {

            Get("/v2/resources/" + URLEncoder.encode("http://rdfh.ch/7bbb8e59b703", "UTF-8")) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val receivedJSONLDAsScala: Map[IRI, Any] = JavaUtil.deepJavatoScala(JsonUtils.fromString(responseAs[String])).asInstanceOf[Map[IRI, Any]]

                val expectedJSONLDAsScala: Map[IRI, Any] = JavaUtil.deepJavatoScala(JsonUtils.fromString(FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/NarrenschiffFirstPage.jsonld")))).asInstanceOf[Map[String, Any]]

                assert(receivedJSONLDAsScala("@context") == expectedJSONLDAsScala("@context"), "@context incorrect")

            }

        }

    }

}