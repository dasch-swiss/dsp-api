/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2.compareJSONLDForMappingCreationResponse
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.v2.StandoffRouteV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1.ANYTHING_PROJECT_IRI
import org.knora.webapi.util.FileUtil

import scala.concurrent.ExecutionContextExecutor

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

  private val standoffPath = new StandoffRouteV2(routeData).knoraApiPath

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val anythingUser = SharedTestDataADM.anythingUser1
  private val anythingUserEmail = anythingUser.email

  private val password = SharedTestDataADM.testPass

  object RequestParams {

    val pathToLetterMapping = "test_data/test_route/texts/mappingForLetter.xml"

    val pathToLetterXML = "test_data/test_route/texts/letter.xml"

    val pathToLetter2XML = "test_data/test_route/texts/letter2.xml"

    val pathToLetter3XML = "test_data/test_route/texts/letter3.xml"

    // Standard HTML is the html code that can be translated into Standoff markup with the OntologyConstants.KnoraBase.StandardMapping
    val pathToStandardHTML = "test_data/test_route/texts/StandardHTML.xml"

    val pathToHTMLMapping = "test_data/test_route/texts/mappingForHTML.xml"

    val pathToHTML = "test_data/test_route/texts/HTML.xml"

  }

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
  )

  "The Standoff v2 Endpoint" should {

    "create a mapping from a XML" in {

      val xmlFileToSend = Paths.get(RequestParams.pathToLetterMapping)

      val mappingParams =
        s"""
           |{
           |    "knora-api:mappingHasName": "LetterMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$ANYTHING_PROJECT_IRI"
           |    },
           |    "rdfs:label": "letter mapping",
           |    "@context": {
           |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
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
          HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), xmlFileToSend),
          Map("filename" -> "brokenMapping.xml")
        )
      )

      // create standoff from XML
      Post("/v2/mapping", formDataMapping) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      ) ~> standoffPath ~> check {

        assert(
          status == StatusCodes.OK,
          "creation of a mapping returned a non successful HTTP status code: " + responseAs[String]
        )

        val expectedAnswerJSONLD =
          FileUtil.readTextFile(Paths.get("test_data/standoffR2RV2/mappingCreationResponse.jsonld"))

        compareJSONLDForMappingCreationResponse(
          expectedJSONLD = expectedAnswerJSONLD,
          receivedJSONLD = responseAs[String]
        )
      }
    }
  }
}
