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

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.SharedTestDataV1.ANYTHING_PROJECT_IRI
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2.compareJSONLDForMappingCreationResponse
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{ResponderManager, _}
import org.knora.webapi.routing.v2.StandoffRouteV2
import org.knora.webapi.store._
import org.knora.webapi.util.FileUtil

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}


/**
  * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class StandoffRouteV2R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val standoffPath = StandoffRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUser = SharedTestDataADM.anythingUser1
    private val anythingUserEmail = anythingUser.email

    private val password = "test"

    object RequestParams {

        val pathToLetterMapping = "_test_data/test_route/texts/mappingForLetter.xml"

        val pathToLetterXML = "_test_data/test_route/texts/letter.xml"

        val pathToLetter2XML = "_test_data/test_route/texts/letter2.xml"

        val pathToLetter3XML = "_test_data/test_route/texts/letter3.xml"

        // Standard HTML is the html code that can be translated into Standoff markup with the OntologyConstants.KnoraBase.StandardMapping
        val pathToStandardHTML = "_test_data/test_route/texts/StandardHTML.xml"

        val pathToHTMLMapping = "_test_data/test_route/texts/mappingForHTML.xml"

        val pathToHTML = "_test_data/test_route/texts/HTML.xml"

    }

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")

    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The Standoff v2 Endpoint" should {

        "create a mapping from a XML" in {

            val xmlFileToSend = new File(RequestParams.pathToLetterMapping)

            val mappingParams =
                s"""
                   |{
                   |    "knora-api:mappingHasName": "LetterMapping",
                   |    "knora-api:attachedToProject": "$ANYTHING_PROJECT_IRI",
                   |    "rdfs:label": "letter mapping",
                   |    "@context": {
                   |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            val formDataMapping = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, mappingParams)
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), xmlFileToSend.toPath),
                    Map("filename" -> "brokenMapping.xml")
                )
            )

            // create standoff from XML
            Post("/v2/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> standoffPath ~> check {

                assert(status == StatusCodes.OK, "creation of a mapping returned a non successful HTTP status code: " + responseAs[String])

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/standoffR2RV2/mappingCreationResponse.jsonld"))

                compareJSONLDForMappingCreationResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }


        }

    }
}