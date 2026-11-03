/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.junit.runner.RunWith
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.*
import zio.json.ast.*
import zio.test.*

import java.nio.file.Path
import java.nio.file.Paths

import dsp.valueobjects.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase as KB
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.responders.IriService
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

@RunWith(classOf[DspZTestJUnitRunner])
class StandoffEndpointsE2ESpec extends E2EZSpec {

  private val pathToXMLWithStandardMapping                  = "test_data/test_route/texts/StandardHTML.xml"
  private val pathToLetterMapping                           = "test_data/test_route/texts/mappingForLetter.xml"
  private val pathToFreetestCustomMapping                   = "test_data/test_route/texts/freetestCustomMapping.xml"
  private val pathToFreetestCustomMappingWithTransformation =
    "test_data/test_route/texts/freetestCustomMappingWithTransformation.xml"
  private val pathToFreetestXMLTextValue                 = "test_data/test_route/texts/freetestXMLTextValue.xml"
  private val freetestXSLTFile                           = "freetestCustomMappingTransformation.xsl"
  private val pathToFreetestXSLTFile                     = s"test_data/test_route/texts/$freetestXSLTFile"
  private val freetestCustomMappingIRI                   = s"$anythingProjectIri/mappings/FreetestCustomMapping"
  private val freetestCustomMappingWithTransformationIRI =
    s"$anythingProjectIri/mappings/FreetestCustomMappingWithTransformation"
  private val freetestOntologyIRI = "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
  private val freetestXSLTIRI     = "http://rdfh.ch/0001/xYSnl8dmTw2RM6KQGVqNDA"

  private val freetestTextValueIRI                          = new MutableTestIri
  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  override val rdfDataObjects: List[RdfDataObject] = List(
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

  private def mappingBody(mappingName: String, label: String, xmlFile: Path, projectIri: ProjectIri) = {
    val json = Json
      .Obj(
        ("knora-api:mappingHasName", Json.Str(mappingName)),
        ("knora-api:attachedToProject", Json.Obj("@id", Json.Str(projectIri.value))),
        ("rdfs:label", Json.Str(label)),
        (
          "@context",
          Json.Obj(
            ("rdfs", Json.Str(Rdfs.RdfsPrefixExpansion)),
            ("knora-api", Json.Str(KA.KnoraApiV2PrefixExpansion)),
          ),
        ),
      )
    Seq(
      multipart("json", json.toString()).contentType("application/json"),
      multipartFile("xml", xmlFile).contentType("text/xml(UTF-8)"),
    )
  }

  private def postMapping(mappingName: String, user: User, projectIri: ProjectIri = anythingProjectIri) =
    TestApiClient.postMultiPart[Json](
      uri"/v2/mapping",
      mappingBody(mappingName, "label", Paths.get(pathToLetterMapping), projectIri),
      user,
    )

  private def createMapping(
    mappingName: String,
    label: String,
    xmlFile: Path,
  ): ZIO[TestApiClient, Nothing, JsonLDDocument] =
    for {
      response <- TestApiClient
                    .postMultiPart[Json](
                      uri"/v2/mapping",
                      mappingBody(mappingName, label, xmlFile, anythingProjectIri),
                      anythingAdminUser,
                    )
                    .orDie
      jsonLd <- response.assert200.map(_.toString).mapAttempt(JsonLDUtil.parseJsonLD).orDie
    } yield jsonLd

  // Denied requests use this name; the project admin creates it last, which fails if a denied request wrote it.
  private val deniedMappingName = "DeniedMapping"

  private def mappingExists(mappingName: String) =
    ZIO
      .fromEither(StandoffMappingIri.from(anythingProjectIri, mappingName))
      .mapError(Exception(_))
      .flatMap(iri => ZIO.serviceWithZIO[IriService](_.checkIriExists(iri.value)))

  private def createResourceWithTextValue(
    xmlContent: String,
    mappingIRI: String,
  ): RIO[TestApiClient, JsonLDDocument] = {
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

    for {
      response <- TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity.toString(), anythingUser1).orDie
      jsonLd   <- response.assert200.mapAttempt(JsonLDUtil.parseJsonLD).orDie
    } yield jsonLd
  }

  private def getTextValueAsDocument(iri: ResourceIri): ZIO[TestApiClient, Throwable, JsonLDDocument] =
    TestApiClient.getJsonLdDocument(uri"/v2/resources/$iri", anythingUser1).flatMap(_.assert200)

  override val e2eSpec: Spec[env, Any] = suite("The Standoff v2 Endpoint")(
    suite("authorization of POST /v2/mapping")(
      test("reject an anonymous user and write no mapping") {
        for {
          response <- TestApiClient.postMultiPart[Json](
                        uri"/v2/mapping",
                        mappingBody(deniedMappingName, "label", Paths.get(pathToLetterMapping), anythingProjectIri),
                      )
          exists <- mappingExists(deniedMappingName)
        } yield assertTrue(response.code == StatusCode.Unauthorized, !exists)
      },
      test("return forbidden for a user without a project and write no mapping") {
        for {
          response <- postMapping(deniedMappingName, normalUser)
          exists   <- mappingExists(deniedMappingName)
        } yield assertTrue(response.code == StatusCode.Forbidden, !exists)
      },
      test("return forbidden for a project member who is not project admin and write no mapping") {
        for {
          response <- postMapping(deniedMappingName, anythingUser1)
          exists   <- mappingExists(deniedMappingName)
        } yield assertTrue(response.code == StatusCode.Forbidden, !exists)
      },
      test("return forbidden for the project admin of another project and write no mapping") {
        for {
          response <- postMapping(deniedMappingName, incunabulaProjectAdminUser)
          exists   <- mappingExists(deniedMappingName)
        } yield assertTrue(response.code == StatusCode.Forbidden, !exists)
      },
      test("reject a body naming a non-existent project with 403") {
        postMapping("UnknownProjectMapping", anythingAdminUser, ProjectIri.makeNew)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("create a mapping as project admin") {
        for {
          _      <- postMapping(deniedMappingName, anythingAdminUser).flatMap(_.assert200)
          exists <- mappingExists(deniedMappingName)
        } yield assertTrue(exists)
      },
      test("create a mapping as system admin") {
        for {
          _      <- postMapping("SystemAdminMapping", rootUser).flatMap(_.assert200)
          exists <- mappingExists("SystemAdminMapping")
        } yield assertTrue(exists)
      },
    ),
    test("create a text value with standard mapping") {
      for {
        xmlContent <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping)))
        document   <- createResourceWithTextValue(xmlContent, KB.StandardMapping)
        valueIri    = document.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        _           = freetestTextValueIRI.set(valueIri)
      } yield assertCompletes
    },
    test("return XML but no HTML for a resource with standard mapping") {
      for {
        xmlContent       <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToXMLWithStandardMapping)))
        responseDocument <- getTextValueAsDocument(freetestTextValueIRI.asResourceIri)
        value            <- ZIO.fromEither(responseDocument.body.getRequiredObject(s"${freetestOntologyIRI}hasText"))
        valueType        <- ZIO.fromEither(value.getRequiredString(JsonLDKeywords.TYPE))
        mapping          <- ZIO.fromEither(
                     value.getRequiredObject(KA.TextValueHasMapping).flatMap(_.getRequiredString(JsonLDKeywords.ID)),
                   )
        actualXml     <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml))
        xmlDiff        = DiffBuilder.compare(Input.fromString(xmlContent)).withTest(Input.fromString(actualXml)).build()
        valueAsHtml   <- ZIO.fromEither(value.getString(KA.TextValueAsHtml))
        valueAsString <- ZIO.fromEither(value.getString(KA.ValueAsString))
      } yield assertTrue(
        valueType == KA.TextValue,
        mapping == KB.StandardMapping,
        !xmlDiff.hasDifferences,
        valueAsHtml.isEmpty,
        valueAsString.isEmpty,
      )
    },
    test("create a mapping from a XML") {
      val expected =
        FileUtil.readAsJsonLd(Paths.get("test_data/generated_test_data/standoffR2RV2/mappingCreationResponse.jsonld"))
      createMapping("LetterMapping", "letter mapping", Paths.get(pathToLetterMapping))
        .map(actual => assertTrue(expected == actual))
    },
    test("create a custom mapping for XML in freetest") {
      createMapping("FreetestCustomMapping", "label", Paths.get(pathToFreetestCustomMapping))
        .flatMap(jsonLd => ZIO.fromEither(jsonLd.body.getRequiredString("@id")).mapError(Exception(_)).orDie)
        .map(mappingIRI => assertTrue(mappingIRI == freetestCustomMappingIRI))
    },
    test("create a text value with the freetext custom mapping") {
      for {
        xmlContent <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue)))
        document   <- createResourceWithTextValue(xmlContent, freetestCustomMappingIRI)
        _           = freetestTextValueIRI.set(document.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun))
      } yield assertCompletes
    },
    test("return XML but no HTML, as there is no transformation provided") {
      for {
        xmlContent <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue)))
        document   <- getTextValueAsDocument(freetestTextValueIRI.asResourceIri)
        value      <- ZIO.fromEither(document.body.getRequiredObject(s"${freetestOntologyIRI}hasText"))
        valueType  <- ZIO.fromEither(value.getRequiredString(JsonLDKeywords.TYPE))
        mapping    <- ZIO.fromEither(
                     value.getRequiredObject(KA.TextValueHasMapping).flatMap(_.getRequiredString(JsonLDKeywords.ID)),
                   )
        textValueAsXml  <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml))
        textValueAsHtml <- ZIO.fromEither(value.getString(KA.TextValueAsHtml))
        valueAsString   <- ZIO.fromEither(value.getString(KA.ValueAsString))

      } yield assertTrue(
        valueType == KA.TextValue,
        mapping == freetestCustomMappingIRI,
        textValueAsXml == xmlContent,
        textValueAsHtml.isEmpty,
        valueAsString.isEmpty,
      )
    },
    test("create a custom mapping with an XSL transformation") {
      for {
        uploadedFile  <- TestDspIngestClient.uploadFile(Paths.get(pathToFreetestXSLTFile), anythingShortcode)
        uploadFileJson = UploadFileRequest
                           .make(
                             fileType = FileType.TextFile,
                             internalFilename = uploadedFile.internalFilename,
                             resourceIRI = Some(freetestXSLTIRI),
                           )
                           .toJsonLd(className = Some("XSLTransformation"))
        response <- TestApiClient.postJsonLd(uri"/v2/resources", uploadFileJson, anythingUser1)
        jsonLd   <- response.assert200.mapAttempt(JsonLDUtil.parseJsonLD).orDie
        id       <- ZIO.fromEither(jsonLd.body.getRequiredString(JsonLDKeywords.ID))
        mapping  <- createMapping(
                     "FreetestCustomMappingWithTransformation",
                     "label",
                     Paths.get(pathToFreetestCustomMappingWithTransformation),
                   )
        mappingIRI <- ZIO.fromEither(mapping.body.getRequiredString("@id"))
      } yield assertTrue(id == freetestXSLTIRI, mappingIRI == freetestCustomMappingWithTransformationIRI)
    },
    test("create a text value with the freetext custom mapping and transformation") {
      for {
        xmlContent               <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue)))
        resourceResponseDocument <- createResourceWithTextValue(xmlContent, freetestCustomMappingWithTransformationIRI)
        _                         = freetestTextValueIRI.set(
              resourceResponseDocument.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun),
            )
      } yield assertCompletes
    },
    test("return XML and HTML rendering of the standoff") {
      for {
        xmlContent  <- ZIO.attempt(FileUtil.readTextFile(Paths.get(pathToFreetestXMLTextValue)))
        expectedHTML = Some("<div>\n    <p> This is a <i>sample</i> of standoff text. </p>\n</div>")
        document    <- getTextValueAsDocument(freetestTextValueIRI.asResourceIri)
        value       <- ZIO.fromEither(document.body.getRequiredObject(s"${freetestOntologyIRI}hasText"))
        valueType   <- ZIO.fromEither(value.getRequiredString(JsonLDKeywords.TYPE))
        mapping     <- ZIO.fromEither(
                     value.getRequiredObject(KA.TextValueHasMapping).flatMap(_.getRequiredString(JsonLDKeywords.ID)),
                   )
        textValueAsXml  <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml))
        textValueAsHtml <- ZIO.fromEither(value.getString(KA.TextValueAsHtml))
        valueAsString   <- ZIO.fromEither(value.getString(KA.ValueAsString))
      } yield assertTrue(
        valueType == KA.TextValue,
        mapping == freetestCustomMappingWithTransformationIRI,
        textValueAsXml == xmlContent,
        textValueAsHtml == expectedHTML,
        valueAsString.isEmpty,
      )
    },
  )
}
