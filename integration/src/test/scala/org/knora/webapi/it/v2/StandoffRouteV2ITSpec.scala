/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.apache.pekko
import org.apache.pekko.http.javadsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import sttp.client4.*
import zio.json.*
import zio.json.ast.*

import java.nio.file.Paths

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ResponseCheckerV2.compareJSONLDForMappingCreationResponse
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase as KB
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

/**
 * Integration test specification for the standoff endpoint.
 */
class StandoffRouteV2ITSpec extends E2ESpec with AuthenticationV2JsonProtocol {

  val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

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
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(
      path = "test_data/project_data/freetest-data.ttl",
      name = "http://www.knora.org/data/0001/freetest",
    ),
  )

  def createMapping(mappingPath: String, mappingName: String): HttpResponse = {
    val jsonPart = Json
      .Obj(
        ("knora-api:mappingHasName", Json.Str(mappingName)),
        ("knora-api:attachedToProject", Json.Obj("@id", Json.Str(anythingProjectIri.value))),
        ("rdfs:label", Json.Str("custom mapping")),
        (
          "@context",
          Json.Obj(
            ("rdfs", Json.Str(Rdfs.RdfsPrefixExpansion)),
            ("knora-api", Json.Str(KA.KnoraApiV2PrefixExpansion)),
          ),
        ),
      )

    val xmlPart = Paths.get(mappingPath)

    val formData = Multipart.FormData(
      Multipart.FormData.BodyPart("json", HttpEntity(ContentTypes.`application/json`, jsonPart.toString())),
      Multipart.FormData.BodyPart("xml", HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, xmlPart)),
    )
    singleAwaitingRequest(
      Post(baseApiUrl + "/v2/mapping", formData) ~>
        addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
    )
  }

  def createResourceWithTextValue(xmlContent: String, mappingIRI: String): HttpResponse = {
    val jsonLDEntity = Json
      .Obj(
        ("@type", Json.Str("freetest:FreeTest")),
        ("knora-api:attachedToProject", Json.Obj("@id", Json.Str("http://rdfh.ch/projects/0001"))),
        ("rdfs:label", Json.Str("obj_inst1")),
        (
          "freetest:hasText",
          Json.Obj(
            ("@type", Json.Str("knora-api:TextValue")),
            ("knora-api:textValueAsXml", Json.Str(xmlContent)),
            ("knora-api:textValueHasMapping", Json.Obj("@id", Json.Str(mappingIRI))),
          ),
        ),
        (
          "@context",
          Json.Obj(
            ("anything", Json.Str("http://0.0.0.0:3333/ontology/0001/anything/v2#")),
            ("freetest", Json.Str(freetestOntologyIRI)),
            ("rdf", Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#")),
            ("rdfs", Json.Str("http://www.w3.org/2000/01/rdf-schema#")),
            ("knora-api", Json.Str("http://api.knora.org/ontology/knora-api/v2#")),
          ),
        ),
      )

    val resourceRequest = Post(
      s"$baseApiUrl/v2/resources",
      HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity.toString()),
    ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
    singleAwaitingRequest(resourceRequest)
  }

  def getTextValueAsDocument(iri: String): JsonLDDocument = UnsafeZioRun.runOrThrow(
    TestApiClient.getJsonLdDocument(uri"/v2/resources/$iri", anythingUser1).flatMap(_.assert200),
  )

  "The Standoff v2 Endpoint" should {
    "create a text value with standard mapping" in {
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = KB.StandardMapping,
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          validationFun,
        ),
      )
    }

    "return XML but no HTML for a resource with standard mapping" in {
      val xmlContent       = FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping))
      val responseDocument = getTextValueAsDocument(freetestTextValueIRI.get)
      val textValueObject = responseDocument.body
        .getRequiredObject(s"${freetestOntologyIRI}hasText")
        .fold(e => throw BadRequestException(e), identity)
      textValueObject
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity) should equal(
        KA.TextValue,
      )
      textValueObject
        .getRequiredObject(KA.TextValueHasMapping)
        .flatMap(_.getRequiredString(JsonLDKeywords.ID))
        .fold(e => throw BadRequestException(e), identity) should equal(KB.StandardMapping)
      val retrievedXML = textValueObject
        .getRequiredString(KA.TextValueAsXml)
        .fold(msg => throw BadRequestException(msg), identity)
      val xmlDiff: Diff = DiffBuilder
        .compare(Input.fromString(xmlContent))
        .withTest(Input.fromString(retrievedXML))
        .build()
      xmlDiff.hasDifferences should be(false)
      textValueObject
        .getString(KA.TextValueAsHtml)
        .fold(msg => throw BadRequestException(msg), identity) should equal(None)
      textValueObject
        .getString(KA.ValueAsString)
        .fold(msg => throw BadRequestException(msg), identity) should equal(None)
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
           |        "rdfs": "${Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${KA.KnoraApiV2PrefixExpansion}"
           |    }
           |}
                """.stripMargin

      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, mappingParams),
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity.fromPath(MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`), xmlFileToSend),
          Map("filename" -> "brokenMapping.xml"),
        ),
      )

      // create standoff from XML
      val request = Post(baseApiUrl + "/v2/mapping", formDataMapping) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password),
      )
      val response = singleAwaitingRequest(request)
      val status   = response.status
      val text     = responseToString(response)

      assert(
        status == StatusCodes.OK,
        s"creation of a mapping returned a non successful HTTP status code: $text",
      )

      val expectedAnswerJSONLD =
        FileUtil.readTextFile(
          Paths.get("../test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"),
        )

      compareJSONLDForMappingCreationResponse(
        expectedJSONLD = expectedAnswerJSONLD,
        receivedJSONLD = text,
      )
    }

    "create a custom mapping for XML in freetest" in {
      // define custom XML to standoff mapping
      val mappingResponse         = createMapping(pathToFreetestCustomMapping, "FreetestCustomMapping")
      val mappingResponseDocument = responseToJsonLDDocument(mappingResponse)
      mappingResponse.status should equal(StatusCodes.OK)

      val mappingIRI =
        mappingResponseDocument.body.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
      mappingIRI should equal(freetestCustomMappingIRI)
    }

    "create a text value with the freetext custom mapping" in {
      // create a resource with a TextValue with custom mapping
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = freetestCustomMappingIRI,
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          validationFun,
        ),
      )
    }

    "return XML but no HTML, as there is no transformation provided" in {
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))

      val responseDocument = getTextValueAsDocument(freetestTextValueIRI.get)
      val textValueObject = responseDocument.body
        .getRequiredObject(s"${freetestOntologyIRI}hasText")
        .fold(e => throw BadRequestException(e), identity)
      textValueObject
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity) should equal(
        KA.TextValue,
      )
      textValueObject
        .getRequiredObject(KA.TextValueHasMapping)
        .flatMap(_.getRequiredString(JsonLDKeywords.ID))
        .fold(msg => throw BadRequestException(msg), identity) should equal(freetestCustomMappingIRI)
      textValueObject
        .getRequiredString(KA.TextValueAsXml)
        .fold(msg => throw BadRequestException(msg), identity) should equal(xmlContent)
      textValueObject
        .getString(KA.TextValueAsHtml)
        .fold(msg => throw BadRequestException(msg), identity) should equal(None)
      textValueObject
        .getString(KA.ValueAsString)
        .fold(msg => throw BadRequestException(msg), identity) should equal(None)
    }

    "create a custom mapping with an XSL transformation" in {
      val uploadedFile =
        UnsafeZioRun.runOrThrow(TestDspIngestClient.uploadFile(Paths.get(pathToFreetestXSLTFile), anythingShortcode))

      // create FileRepresentation in API
      val uploadFileJson = UploadFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIRI = Some(freetestXSLTIRI),
        )
        .toJsonLd(className = Some("XSLTransformation"))
      val fileRepresentationRequest = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, uploadFileJson),
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val fileRepresentationResponse = singleAwaitingRequest(fileRepresentationRequest)
      assert(StatusCodes.OK == fileRepresentationResponse.status, responseToString(fileRepresentationResponse))
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(fileRepresentationResponse)
      responseJsonDoc.body
        .getRequiredString(JsonLDKeywords.ID)
        .fold(msg => throw BadRequestException(msg), identity) should equal(freetestXSLTIRI)

      // add a mapping that refers to the transformation
      val mappingResponse =
        createMapping(pathToFreetestCustomMappingWithTransformation, "FreetestCustomMappingWithTransformation")
      val mappingResponseDocument = responseToJsonLDDocument(mappingResponse)
      mappingResponse.status should equal(StatusCodes.OK)
      val mappingIRI =
        mappingResponseDocument.body.getRequiredString("@id").fold(msg => throw BadRequestException(msg), identity)
      mappingIRI should equal(freetestCustomMappingWithTranformationIRI)
    }

    "create a text value with the freetext custom mapping and transformation" in {
      // create a resource with a TextValue with custom mapping
      val xmlContent = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val response = createResourceWithTextValue(
        xmlContent = xmlContent,
        mappingIRI = freetestCustomMappingWithTranformationIRI,
      )
      assert(response.status == StatusCodes.OK, responseToString(response))
      val resourceResponseDocument: JsonLDDocument = responseToJsonLDDocument(response)
      freetestTextValueIRI.set(
        resourceResponseDocument.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          validationFun,
        ),
      )
    }

    "return XML and HTML rendering of the standoff" in {
      val xmlContent   = FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue))
      val expectedHTML = Some("<div>\n    <p> This is a <i>sample</i> of standoff text. </p>\n</div>")

      val responseDocument = getTextValueAsDocument(freetestTextValueIRI.get)
      val textValueObject = responseDocument.body
        .getRequiredObject(s"${freetestOntologyIRI}hasText")
        .fold(e => throw BadRequestException(e), identity)
      textValueObject
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity) should equal(
        KA.TextValue,
      )
      textValueObject
        .getRequiredObject(KA.TextValueHasMapping)
        .flatMap(_.getRequiredString(JsonLDKeywords.ID))
        .fold(e => throw BadRequestException(e), identity) should equal(freetestCustomMappingWithTranformationIRI)
      textValueObject
        .getRequiredString(KA.TextValueAsXml)
        .fold(msg => throw BadRequestException(msg), identity) should equal(xmlContent)
      textValueObject
        .getString(KA.TextValueAsHtml)
        .fold(msg => throw BadRequestException(msg), identity) should equal(expectedHTML)
      textValueObject
        .getString(KA.ValueAsString)
        .fold(msg => throw BadRequestException(msg), identity) should equal(None)
    }
  }
}
