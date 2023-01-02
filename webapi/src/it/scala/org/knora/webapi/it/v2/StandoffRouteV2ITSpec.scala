/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import spray.json._

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration._

import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2.compareJSONLDForMappingCreationResponse
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol._
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginResponse
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.models.standoffmodels.DefineStandoffMapping
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1.anythingProjectIri
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

/**
 * Integration test specification for the standoff endpoint.
 */
class StandoffRouteV2ITSpec extends ITKnoraLiveSpec with AuthenticationV2JsonProtocol {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUser      = SharedTestDataADM.anythingUser1
  private val anythingUserEmail = anythingUser.email
  private val password          = SharedTestDataADM.testPass

  private val pathToXMLWithStandardMapping = "../test_data/test_route/texts/StandardHTML.xml"
  private val pathToLetterMapping          = "../test_data/test_route/texts/mappingForLetter.xml"
  private val pathToFreetestCustomMapping  = "../test_data/test_route/texts/freetestCustomMapping.xml"
  private val pathToFreetestCustomMappingWithTransformation =
    "../test_data/test_route/texts/freetestCustomMappingWithTransformation.xml"
  private val pathToFreetestXMLTextValue = "../test_data/test_route/texts/freetestXMLTextValue.xml"
  private val freetestXSLTFile           = "freetestCustomMappingTransformation.xsl"
  private val pathToFreetestXSLTFile     = s"../test_data/test_route/texts/$freetestXSLTFile"
  private val freetestCustomMappingIRI   = s"$anythingProjectIri/mappings/FreetestCustomMapping"
  private val freetestCustomMappingWithTranformationIRI =
    s"$anythingProjectIri/mappings/FreetestCustomMappingWithTransformation"
  private val freetestOntologyIRI  = "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
  private val freetestTextValueIRI = new MutableTestIri
  private val freetestXSLTIRI      = "http://rdfh.ch/0001/xYSnl8dmTw2RM6KQGVqNDA"

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    ),
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
  )

  def createMapping(mappingPath: String, mappingName: String): HttpResponse = {
    val mappingFile   = Paths.get(mappingPath)
    val mappingParams = DefineStandoffMapping.make(mappingName = mappingName).toJSONLD()

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
    singleAwaitingRequest(mappingRequest)
  }

  def createResourceWithTextValue(xmlContent: String, mappingIRI: String): HttpResponse = {
    val jsonLDEntity = Map(
      "@type" -> "freetest:FreeTest".toJson,
      "knora-api:attachedToProject" -> Map(
        "@id" -> "http://rdfh.ch/projects/0001".toJson
      ).toJson,
      "rdfs:label" -> "obj_inst1".toJson,
      "freetest:hasText" -> Map(
        "@type"                    -> "knora-api:TextValue".toJson,
        "knora-api:textValueAsXml" -> xmlContent.toJson,
        "knora-api:textValueHasMapping" -> Map(
          "@id" -> mappingIRI.toJson
        ).toJson
      ).toJson,
      "@context" -> Map(
        "anything"  -> "http://0.0.0.0:3333/ontology/0001/anything/v2#".toJson,
        "freetest"  -> freetestOntologyIRI.toJson,
        "rdf"       -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#".toJson,
        "rdfs"      -> "http://www.w3.org/2000/01/rdf-schema#".toJson,
        "knora-api" -> "http://api.knora.org/ontology/knora-api/v2#".toJson
      ).toJson
    ).toJson.prettyPrint

    val resourceRequest = Post(
      s"$baseApiUrl/v2/resources",
      HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
    ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
    singleAwaitingRequest(resourceRequest)
  }

  def getTextValueAsDocument(iri: String): JsonLDDocument = {
    val request = Get(
      s"$baseApiUrl/v2/resources/$iri"
    ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
    getResponseJsonLD(request)
  }

  "The Standoff v2 Endpoint" should {
    "create a text value with standard mapping" in {
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = OntologyConstants.KnoraBase.StandardMapping
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          stringFormatter.validateAndEscapeIri
        )
      )
    }

    "return XML but no HTML for a resource with standard mapping" in {
      val valueIRI   = URLEncoder.encode(freetestTextValueIRI.get, "UTF-8")
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping))

      val responseDocument = getTextValueAsDocument(valueIRI)
      val textValueObject  = responseDocument.body.requireObject(s"${freetestOntologyIRI}hasText")
      textValueObject.requireString(JsonLDKeywords.TYPE) should equal(OntologyConstants.KnoraApiV2Complex.TextValue)
      textValueObject
        .requireObject(OntologyConstants.KnoraApiV2Complex.TextValueHasMapping)
        .requireString(JsonLDKeywords.ID) should equal(OntologyConstants.KnoraBase.StandardMapping)
      val retrievedXML = textValueObject.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(xmlContent))
        .withTest(Input.fromString(retrievedXML))
        .build()
      xmlDiff.hasDifferences should be(false)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml) should equal(None)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should equal(None)
    }

    "create a mapping from a XML" in {
      val xmlFileToSend = Paths.get(pathToLetterMapping)

      val mappingParams =
        s"""
           |{
           |    "knora-api:mappingHasName": "LetterMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
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
      val status   = response.status
      val text     = responseToString(response)

      assert(
        status == StatusCodes.OK,
        s"creation of a mapping returned a non successful HTTP status code: $text"
      )

      val expectedAnswerJSONLD =
        FileUtil.readTextFile(Paths.get("../test_data/standoffR2RV2/mappingCreationResponse.jsonld"))

      compareJSONLDForMappingCreationResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = text
      )
    }

    "create a custom mapping for XML in freetest" in {
      // define custom XML to standoff mapping
      val mappingResponse         = createMapping(pathToFreetestCustomMapping, "FreetestCustomMapping")
      val mappingResponseDocument = responseToJsonLDDocument(mappingResponse)
      mappingResponse.status should equal(StatusCodes.OK)

      val mappingIRI = mappingResponseDocument.body.requireString("@id")
      mappingIRI should equal(freetestCustomMappingIRI)
    }

    "create a text value with the freetext custom mapping" in {
      // create a resource with a TextValue with custom mapping
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = freetestCustomMappingIRI
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          stringFormatter.validateAndEscapeIri
        )
      )
    }

    "return XML but no HTML, as there is no transformation provided" in {
      val valueIRI   = URLEncoder.encode(freetestTextValueIRI.get, "UTF-8")
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))

      val responseDocument = getTextValueAsDocument(valueIRI)
      val textValueObject  = responseDocument.body.requireObject(s"${freetestOntologyIRI}hasText")
      textValueObject.requireString(JsonLDKeywords.TYPE) should equal(OntologyConstants.KnoraApiV2Complex.TextValue)
      textValueObject
        .requireObject(OntologyConstants.KnoraApiV2Complex.TextValueHasMapping)
        .requireString(JsonLDKeywords.ID) should equal(freetestCustomMappingIRI)
      textValueObject.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) should equal(xmlContent)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml) should equal(None)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should equal(None)
    }

    "create a custom mapping with an XSL transformation" in {
      // get authentication token
      val params = Map(
        "email"    -> "root@example.com",
        "password" -> "test"
      ).toJson.compactPrint
      val loginRequest                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val loginResponse: HttpResponse = singleAwaitingRequest(loginRequest)
      assert(loginResponse.status == StatusCodes.OK, responseToString(loginResponse))
      val loginToken = Await.result(Unmarshal(loginResponse.entity).to[LoginResponse], 1.seconds).token

      // upload XSLT file to SIPI
      val sipiFormData = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "file",
          HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, Paths.get(pathToFreetestXSLTFile)),
          Map("filename" -> freetestXSLTFile)
        )
      )
      val sipiRequest  = Post(s"${baseInternalSipiUrl}/upload?token=$loginToken", sipiFormData)
      val sipiResponse = singleAwaitingRequest(sipiRequest)
      val uploadedFile = responseToString(sipiResponse).parseJson.asJsObject
        .convertTo[SipiUploadResponse]
        .uploadedFiles
        .head

      // create FileRepresentation in API
      val uploadFileJson = UploadFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIRI = Some(freetestXSLTIRI)
        )
        .toJsonLd(
          className = Some("XSLTransformation")
        )
      val fileRepresentationRequest = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, uploadFileJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val fileRepresentationResponse = singleAwaitingRequest(fileRepresentationRequest)
      assert(StatusCodes.OK == fileRepresentationResponse.status, responseToString(fileRepresentationResponse))
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(fileRepresentationResponse)
      responseJsonDoc.body.requireString(JsonLDKeywords.ID) should equal(freetestXSLTIRI)

      // add a mapping that refers to the transformation
      val mappingResponse =
        createMapping(pathToFreetestCustomMappingWithTransformation, "FreetestCustomMappingWithTransformation")
      val mappingResponseDocument = responseToJsonLDDocument(mappingResponse)
      mappingResponse.status should equal(StatusCodes.OK)
      val mappingIRI = mappingResponseDocument.body.requireString("@id")
      mappingIRI should equal(freetestCustomMappingWithTranformationIRI)
    }

    "create a text value with the freetext custom mapping and transformation" in {
      // create a resource with a TextValue with custom mapping
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = freetestCustomMappingWithTranformationIRI
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          stringFormatter.validateAndEscapeIri
        )
      )
    }

    "return XML and HTML rendering of the standoff" in {
      val valueIRI     = URLEncoder.encode(freetestTextValueIRI.get, "UTF-8")
      val xmlContent   = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val expectedHTML = Some("<div>\n    <p> This is a <i>sample</i> of standoff text. </p>\n</div>")

      val responseDocument = getTextValueAsDocument(valueIRI)
      val textValueObject  = responseDocument.body.requireObject(s"${freetestOntologyIRI}hasText")
      textValueObject.requireString(JsonLDKeywords.TYPE) should equal(OntologyConstants.KnoraApiV2Complex.TextValue)
      textValueObject
        .requireObject(OntologyConstants.KnoraApiV2Complex.TextValueHasMapping)
        .requireString(JsonLDKeywords.ID) should equal(freetestCustomMappingWithTranformationIRI)
      textValueObject.requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml) should equal(xmlContent)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsHtml) should equal(expectedHTML)
      textValueObject.maybeString(OntologyConstants.KnoraApiV2Complex.ValueAsString) should equal(None)
    }
  }
}
