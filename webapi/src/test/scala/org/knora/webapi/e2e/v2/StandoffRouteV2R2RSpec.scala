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
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDKeywords}
import org.knora.webapi.routing.v2.StandoffRouteV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1.ANYTHING_PROJECT_IRI
import org.knora.webapi.util.FileUtil
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import spray.json._
import spray.json.DefaultJsonProtocol._

import java.net.URLEncoder

/**
 * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class StandoffRouteV2R2RSpec extends E2ESpec {
  private implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    settings.defaultTimeout
  )

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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

  private val pathToFreetestCustomMapping = "test_data/test_route/texts/freetestCustomMapping.xml"
  private val pathToFreetestXMLTextValue = "test_data/test_route/texts/freetestXMLTextValue.xml"
  private val freetestCustomMappingIRI = "http://rdfh.ch/projects/0001/mappings/FreetestCustomMapping"
  private val freetestOntologyIRI = "http://0.0.0.0:3333/ontology/0001/freetest/v2#"

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
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
      val request = Post(baseApiUrl + "/v2/mapping", formDataMapping) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      )
      val response = singleAwaitingRequest(request)
      val status = response.status
      val text = responseToString(response)

      assert(
        status == StatusCodes.OK,
        s"creation of a mapping returned a non successful HTTP status code: $text"
      )

      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("test_data/standoffR2RV2/mappingCreationResponse.jsonld"))

      compareJSONLDForMappingCreationResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = text
      )
    }

    "create a custom mapping for XML in freetest" in {
      // define custom XML to standoff mapping
      val mappingFile = Paths.get(pathToFreetestCustomMapping)
      val mappingParams =
        s"""
           |{
           |    "knora-api:mappingHasName": "FreetestCustomMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$ANYTHING_PROJECT_IRI"
           |    },
           |    "rdfs:label": "custom mapping",
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
          HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), mappingFile)
        )
      )
      val mappingRequest = Post(baseApiUrl + "/v2/mapping", formDataMapping) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      )
      val mappingResponse = singleAwaitingRequest(mappingRequest)
      val mappingResponseDocument = responseToJsonLDDocument(mappingResponse)
      mappingResponse.status should equal(StatusCodes.OK)
      val mappingIRI = mappingResponseDocument.body.requireString("@id")
      mappingIRI should equal(freetestCustomMappingIRI)
    }

    "allow to create a text value with a custom standoff mapping but no XSL transformation" in {
      // create a resource with a TextValue with custom mapping
      val xmlFile = Paths.get(pathToFreetestXMLTextValue)
      val xmlContent = FileUtil.readTextFile(xmlFile)

      val jsonLDEntity = Map(
        "@type" -> "freetest:FreeTest".toJson,
        "knora-api:attachedToProject" -> Map(
          "@id" -> "http://rdfh.ch/projects/0001".toJson
        ).toJson,
        "rdfs:label" -> "obj_inst1".toJson,
        "freetest:hasText" -> Map(
          "@type" -> "knora-api:TextValue".toJson,
          "knora-api:textValueAsXml" -> xmlContent.toJson,
          "knora-api:textValueHasMapping" -> Map(
            "@id" -> freetestCustomMappingIRI.toJson
          ).toJson
        ).toJson,
        "@context" -> Map(
          "anything" -> "http://0.0.0.0:3333/ontology/0001/anything/v2#".toJson,
          "freetest" -> freetestOntologyIRI.toJson,
          "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#".toJson,
          "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#".toJson,
          "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#".toJson
        ).toJson
      ).toJson.prettyPrint

      val resourceRequest = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceResponse: HttpResponse = singleAwaitingRequest(resourceRequest)
      println(resourceResponse)
      assert(resourceResponse.status == StatusCodes.OK, resourceResponse.toString)
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(resourceResponse)
      val resourceIRI: IRI = resourceResponseDocument.body.requireStringWithValidation(
        JsonLDKeywords.ID,
        stringFormatter.validateAndEscapeIri
      )
      println(resourceIRI)
      println(resourceResponse)

      // get the newly created resource back

      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIRI, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)

      val responseDocument = responseToJsonLDDocument(resourceComplexGetResponse)
      val textValueObject = responseDocument.body.requireObject(s"${freetestOntologyIRI}hasText")
      println(textValueObject)
      textValueObject.requireString(JsonLDKeywords.TYPE) should equal(OntologyConstants.KnoraApiV2Complex.TextValue)
      textValueObject
        .requireObject(OntologyConstants.KnoraApiV2Complex.TextValueHasMapping)
        .requireString(JsonLDKeywords.ID) should equal(freetestCustomMappingIRI)
      textValueObject.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) should equal(xmlContent)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml) should equal(None)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should equal(None)
    }

    // TODO: check with XSLT
    // TODO: move stuff to models
    // TODO: revert all ontologies to what they were, to that no upgrade script will be needed
  }
}
