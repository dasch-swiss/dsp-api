/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import sttp.model.Uri.*
import zio.*
import zio.test.*

import java.nio.file.Paths

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.models.filemodels.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingAdminUser
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingOntologyIri
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingShortcode
import org.knora.webapi.slice.admin.domain.model.LegalInfo
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient
import org.knora.webapi.testservices.TestResourcesApiClient
import org.knora.webapi.testservices.TestSipiApiClient
import org.knora.webapi.util.MutableTestIri

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
object KnoraSipiIntegrationV2ITSpec extends E2EZSpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val stillImageResourceIri  = new MutableTestIri
  private val stillImageFileValueIri = new MutableTestIri
  private val pdfResourceIri         = new MutableTestIri
  private val pdfValueIri            = new MutableTestIri
  private val xmlResourceIri         = new MutableTestIri
  private val xmlValueIri            = new MutableTestIri
  private val csvResourceIri         = new MutableTestIri
  private val csvValueIri            = new MutableTestIri
  private val zipResourceIri         = new MutableTestIri
  private val zipValueIri            = new MutableTestIri
  private val wavResourceIri         = new MutableTestIri
  private val wavValueIri            = new MutableTestIri

  private val videoResourceIri = new MutableTestIri
  private val videoValueIri    = new MutableTestIri

  private val marblesOriginalFilename = "marbles.tif"
  private val pathToMarbles           = Paths.get(s"test_data/test_route/images/$marblesOriginalFilename")
  private val marblesWidth            = 1419
  private val marblesHeight           = 1001

  private val trp88OriginalFilename = "Trp88.tiff"
  private val pathToTrp88           = Paths.get(s"test_data/test_route/images/$trp88OriginalFilename")
  private val trp88Width            = 499
  private val trp88Height           = 630

  private val minimalPdfOriginalFilename = "minimal.pdf"
  private val pathToMinimalPdf           = Paths.get(s"test_data/test_route/files/$minimalPdfOriginalFilename")

  private val testPdfOriginalFilename = "test.pdf"
  private val pathToTestPdf           = Paths.get(s"test_data/test_route/files/$testPdfOriginalFilename")

  private val csv1OriginalFilename = "eggs.csv"
  private val pathToCsv1           = Paths.get(s"test_data/test_route/files/$csv1OriginalFilename")

  private val csv2OriginalFilename = "spam.csv"
  private val pathToCsv2           = Paths.get(s"test_data/test_route/files/$csv2OriginalFilename")

  private val xml1OriginalFilename = "test1.xml"
  private val pathToXml1           = Paths.get(s"test_data/test_route/files/$xml1OriginalFilename")

  private val xml2OriginalFilename = "test2.xml"
  private val pathToXml2           = Paths.get(s"test_data/test_route/files/$xml2OriginalFilename")

  private val minimalZipOriginalFilename = "minimal.zip"
  private val pathToMinimalZip           = Paths.get(s"test_data/test_route/files/$minimalZipOriginalFilename")

  private val testZipOriginalFilename = "test.zip"
  private val pathToTestZip           = Paths.get(s"test_data/test_route/files/$testZipOriginalFilename")

  private val test7zOriginalFilename = "test.7z"
  private val pathToTest7z           = Paths.get(s"test_data/test_route/files/$test7zOriginalFilename")

  private val minimalWavOriginalFilename = "minimal.wav"
  private val pathToMinimalWav           = Paths.get(s"test_data/test_route/files/$minimalWavOriginalFilename")

  private val testWavOriginalFilename = "test.wav"
  private val pathToTestWav           = Paths.get(s"test_data/test_route/files/$testWavOriginalFilename")

  private val testVideoOriginalFilename = "testVideo.mp4"
  private val pathToTestVideo           = Paths.get(s"test_data/test_route/files/$testVideoOriginalFilename")

  private val testVideo2OriginalFilename = "testVideo2.mp4"
  private val pathToTestVideo2           = Paths.get(s"test_data/test_route/files/$testVideo2OriginalFilename")

  private val thingDocumentIRI = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingDocument"

  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  private def assertingUnique[A](l: Seq[A]): A = l.toList match {
    case h :: Nil => h
    case _        => throw AssertionException(s"Not unique value: $l out of ${l.length} values.")
  }

  /**
   * Represents the information that Knora returns about an image file value that was created.
   *
   * @param internalFilename the image's internal filename.
   * @param iiifUrl          the image's IIIF URL.
   * @param width            the image's width in pixels.
   * @param height           the image's height in pixels.
   */
  case class SavedImage(internalFilename: String, iiifUrl: String, width: Int, height: Int)

  /**
   * Represents the information that Knora returns about a document file value that was created.
   *
   * @param internalFilename the files's internal filename.
   * @param url              the file's URL.
   */
  case class SavedDocument(internalFilename: String, url: String)

  /**
   * Represents the information that Knora returns about a text file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedTextFile(internalFilename: String, url: String)

  /**
   * Represents the information that Knora returns about an audio file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedAudioFile(internalFilename: String, url: String)

  /**
   * Represents the information that Knora returns about a video file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   */
  case class SavedVideoFile(internalFilename: String, url: String)

  /**
   * Given a JSON-LD document representing a resource, returns a JSON-LD array containing the values of the specified
   * property.
   *
   * @param resource            the JSON-LD document.
   * @param propertyIriInResult the property IRI.
   * @return a JSON-LD array containing the values of the specified property.
   */
  private def getValuesFromResource(resource: JsonLDDocument, propertyIriInResult: SmartIri): JsonLDArray =
    resource.body.getRequiredArray(propertyIriInResult.toString).fold(e => throw BadRequestException(e), identity)

  /**
   * Given a JSON-LD document representing a resource, returns a JSON-LD object representing the expected single
   * value of the specified property.
   *
   * @param resource            the JSON-LD document.
   * @param propertyIriInResult the property IRI.
   * @param expectedValueIri    the IRI of the expected value.
   * @return a JSON-LD object representing the expected single value of the specified property.
   */
  private def getValueFromResource(
    resource: JsonLDDocument,
    propertyIriInResult: SmartIri,
    expectedValueIri: IRI,
  ): JsonLDObject =
    assertingUnique(getValuesFromResource(resource, propertyIriInResult).value.flatMap {
      _.asOpt[JsonLDObject].filter(_.requireStringWithValidation(JsonLDKeywords.ID, validationFun) == expectedValueIri)
    })

  /**
   * Given a JSON-LD object representing a Knora image file value, returns a [[SavedImage]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora image file value.
   * @return a [[SavedImage]] containing the same information.
   */
  private def savedValueToSavedImage(savedValue: JsonLDObject): SavedImage = {
    val internalFilename = savedValue
      .getRequiredString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)
      .fold(msg => throw BadRequestException(msg), identity)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val iiifUrl = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun,
    )

    val width  = savedValue.getRequiredInt(StillImageFileValueHasDimX).fold(e => throw BadRequestException(e), identity)
    val height = savedValue.getRequiredInt(StillImageFileValueHasDimY).fold(e => throw BadRequestException(e), identity)

    SavedImage(
      internalFilename = internalFilename,
      iiifUrl = iiifUrl,
      width = width,
      height = height,
    )
  }

  /**
   * Given a JSON-LD object representing a Knora document file value, returns a [[SavedDocument]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedDocument]] containing the same information.
   */
  private def savedValueToSavedDocument(savedValue: JsonLDObject): SavedDocument = {
    val internalFilename = savedValue
      .getRequiredString(FileValueHasFilename)
      .fold(msg => throw BadRequestException(msg), identity)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun,
    )

    SavedDocument(
      internalFilename = internalFilename,
      url = url,
    )
  }

  /**
   * Given a JSON-LD object representing a Knora text file value, returns a [[SavedTextFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedTextFile]] containing the same information.
   */
  private def savedValueToSavedTextFile(savedValue: JsonLDObject): SavedTextFile = {
    val internalFilename = savedValue
      .getRequiredString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)
      .fold(msg => throw BadRequestException(msg), identity)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun,
    )

    SavedTextFile(
      internalFilename = internalFilename,
      url = url,
    )
  }

  /**
   * Given a JSON-LD object representing a Knora audio file value, returns a [[SavedAudioFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora audio file value.
   * @return a [[SavedAudioFile]] containing the same information.
   */
  private def savedValueToSavedAudioFile(savedValue: JsonLDObject): SavedAudioFile = {
    val internalFilename = savedValue
      .getRequiredString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)
      .fold(msg => throw BadRequestException(msg), identity)

    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun,
    )

    SavedAudioFile(
      internalFilename = internalFilename,
      url = url,
    )
  }

  /**
   * Given a JSON-LD object representing a Knora video file value, returns a [[SavedVideoFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora video file value.
   * @return a [[SavedVideoFile]] containing the same information.
   */
  private def savedValueToSavedVideoFile(savedValue: JsonLDObject): SavedVideoFile = {
    val internalFilename =
      savedValue.getRequiredString(FileValueHasFilename).fold(msg => throw BadRequestException(msg), identity)
    val validationFun: (String, => Nothing) => String = (s, errorFun) =>
      Iri.toSparqlEncodedString(s).getOrElse(errorFun)
    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun,
    )

    SavedVideoFile(internalFilename = internalFilename, url = url)
  }

  override val e2eSpec = suite("The Knora/Sipi integration")(
    test("create a resource with a still image file") {
      for {
        // Upload the image to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMarbles, anythingShortcode)

        // Create the resource in the API.
        response <- TestResourcesApiClient
                      .createStillImageRepresentation(
                        anythingShortcode,
                        anythingOntologyIri,
                        "ThingPicture",
                        uploadedFile,
                        anythingAdminUser,
                        LegalInfo.empty,
                      )
        jsonLd      <- response.assert200.mapAttempt(JsonLDUtil.parseJsonLD)
        resourceIri <- jsonLd.body.getRequiredIdValueAsKnoraDataIri.map(_.toIri)
        _            = stillImageResourceIri.set(resourceIri)
        resource    <- TestResourcesApiClient.getResource(resourceIri).flatMap(_.assert200)

        // Verify resource type
        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues   = getValuesFromResource(resource, HasStillImageFileValue.toSmartIri)
        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = stillImageFileValueIri.set(valueIri.toString)

        savedImage = savedValueToSavedImage(savedValueObj)
      } yield assertTrue(
        uploadedFile.originalFilename == marblesOriginalFilename,
        resourceType.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture",
        savedImage.internalFilename == uploadedFile.internalFilename,
        savedImage.width == marblesWidth,
        savedImage.height == marblesHeight,
      )
    },
    test("create a resource with a still image file that has already been ingested") {
      for {
        // Create the resource in the API.
        jsonLdEntity <-
          ZIO.succeed(
            UploadFileRequest
              .make(fileType = FileType.StillImageFile(), internalFilename = "De6XyNL4H71-D9QxghOuOPJ.jp2")
              .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything"),
          )
        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _      <- TestApiClient.getJsonLd(uri"/v2/resources/$resIri").flatMap(_.assert200)
      } yield assertCompletes
    },
    test("change a still image file value") {
      for {
        // Upload the image to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTrp88, anythingShortcode)

        // JSON describing the new image to the API.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.StillImageFile(),
                           internalFilename = uploadedFile.internalFilename,
                           resourceIri = stillImageResourceIri.get,
                           valueIri = stillImageFileValueIri.get,
                           className = Some("ThingPicture"),
                           ontologyName = "anything",
                         )
                         .toJsonLd

        // Send the JSON in a PUT request to the API.
        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = stillImageFileValueIri.set(resIri.toString)

        resource <- TestApiClient
                      .getJsonLd(uri"/v2/resources/${stillImageResourceIri.get}")
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        savedValue = getValueFromResource(resource, HasStillImageFileValue.toSmartIri, stillImageFileValueIri.get)
        savedImage = savedValueToSavedImage(savedValue)

        // Request the permanently stored image from Sipi.
        _ <- TestSipiApiClient.getFile(savedImage.iiifUrl).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == trp88OriginalFilename,
        savedImage.internalFilename == uploadedFile.internalFilename,
        savedImage.width == trp88Width,
        savedImage.height == trp88Height,
        savedImage.iiifUrl.nonEmpty,
      )
    },
    test("create a resource with a PDF file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMinimalPdf, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.DocumentFile(), internalFilename = uploadedFile.internalFilename)
                         .toJsonLd(className = Some("ThingDocument"), ontologyName = "anything")

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = pdfResourceIri.set(resIri.toString)

        resource <- TestApiClient
                      .getJsonLd(uri"/v2/resources/${pdfResourceIri.get}")
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues   = getValuesFromResource(resource, HasDocumentFileValue.toSmartIri)
        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = pdfValueIri.set(valueIri.toString)

        savedDocument = savedValueToSavedDocument(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedDocument.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == minimalPdfOriginalFilename,
        resourceType.toString == thingDocumentIRI,
        savedDocument.internalFilename == uploadedFile.internalFilename,
        savedDocument.url.nonEmpty,
      )
    },
    test("change a PDF file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTestPdf, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.DocumentFile(),
                           internalFilename = uploadedFile.internalFilename,
                           resourceIri = pdfResourceIri.get,
                           valueIri = pdfValueIri.get,
                           className = Some("ThingDocument"),
                           ontologyName = "anything",
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = pdfValueIri.set(resIri.toString)

        resource <- TestApiClient
                      .getJsonLdDocument(uri"/v2/resources/${pdfResourceIri.get}")
                      .flatMap(_.assert200)

        savedValue    = getValueFromResource(resource, HasDocumentFileValue.toSmartIri, pdfValueIri.get)
        savedDocument = savedValueToSavedDocument(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedDocument.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == testPdfOriginalFilename,
        savedDocument.internalFilename == uploadedFile.internalFilename,
        savedDocument.url.nonEmpty,
      )
    },
    test("not create a document resource if the file is actually a zip file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMinimalZip, anythingShortcode)

        // Create the resource in the API - this should fail.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.DocumentFile(), internalFilename = uploadedFile.internalFilename)
                         .toJsonLd(className = Some("ThingDocument"), ontologyName = "anything")

        _ <- TestApiClient
               .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
               .flatMap(_.assert400)
               .mapAttempt(JsonLDUtil.parseJsonLD)
      } yield assertTrue(uploadedFile.originalFilename == minimalZipOriginalFilename)
    },
    test("create a resource with a CSV file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToCsv1, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resourceIri = response.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        dataIri    <- response.body.getRequiredIdValueAsKnoraDataIri
        _           = csvResourceIri.set(dataIri.toString)

        // Get the new file value from the resource.
        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri").flatMap(_.assert200)

        savedValues   = getValuesFromResource(resource, HasTextFileValue.toSmartIri)
        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = csvValueIri.set(valueIri.toString)

        savedTextFile = savedValueToSavedTextFile(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedTextFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == csv1OriginalFilename,
        savedTextFile.internalFilename == uploadedFile.internalFilename,
        savedTextFile.url.nonEmpty,
      )
    },
    test("change a CSV file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToCsv2, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.TextFile,
                           internalFilename = uploadedFile.internalFilename,
                           resourceIri = csvResourceIri.get,
                           valueIri = csvValueIri.get,
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = csvValueIri.set(resIri.toString)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${csvResourceIri.get}").flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValue    = getValueFromResource(resource, HasTextFileValue.toSmartIri, csvValueIri.get)
        savedTextFile = savedValueToSavedTextFile(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedTextFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == csv2OriginalFilename,
        savedTextFile.internalFilename == uploadedFile.internalFilename,
        savedTextFile.url.nonEmpty,
      )
    },
    test("not create a resource with a still image file that's actually a text file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToCsv1, anythingShortcode)

        // Create the resource in the API - this should fail.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.StillImageFile(), internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        _ <- TestApiClient
               .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
               .flatMap(_.assert400)
               .mapAttempt(JsonLDUtil.parseJsonLD)
      } yield assertTrue(uploadedFile.originalFilename == csv1OriginalFilename)
    },
    test("create a resource with an XML file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToXml1, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        dataIri    <- response.body.getRequiredIdValueAsKnoraDataIri
        _           = xmlResourceIri.set(dataIri.toString)
        resourceIri = response.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)

        resource <- TestApiClient
                      .getJsonLdDocument(uri"/v2/resources/$resourceIri")
                      .flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValues = getValuesFromResource(
                        resource = resource,
                        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
                      )

        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = xmlValueIri.set(valueIri.toString)

        savedTextFile = savedValueToSavedTextFile(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedTextFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == xml1OriginalFilename,
        savedTextFile.internalFilename == uploadedFile.internalFilename,
        savedTextFile.url.nonEmpty,
      )
    },
    test("change an XML file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToXml2, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.TextFile,
                           internalFilename = uploadedFile.internalFilename,
                           resourceIri = xmlResourceIri.get,
                           valueIri = xmlValueIri.get,
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = xmlValueIri.set(resIri.toString)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${xmlResourceIri.get}").flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValue = getValueFromResource(
                       resource = resource,
                       propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
                       expectedValueIri = xmlValueIri.get,
                     )

        savedTextFile = savedValueToSavedTextFile(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedTextFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == xml2OriginalFilename,
        savedTextFile.internalFilename == uploadedFile.internalFilename,
        savedTextFile.url.nonEmpty,
      )
    },
    test("not create a resource of type TextRepresentation with a Zip file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMinimalZip, anythingShortcode)

        // Create the resource in the API - this should fail.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        _ <- TestApiClient
               .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
               .flatMap(_.assert400)
               .mapAttempt(JsonLDUtil.parseJsonLD)
      } yield assertTrue(uploadedFile.originalFilename == minimalZipOriginalFilename)
    },
    test("create a resource of type ArchiveRepresentation with a Zip file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMinimalZip, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri.map(_.toIri)
        _       = zipResourceIri.set(resIri)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${zipResourceIri.get}").flatMap(_.assert200)

        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues = getValuesFromResource(
                        resource = resource,
                        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
                      )

        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = zipValueIri.set(valueIri.toString)

        savedDocument = savedValueToSavedDocument(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedDocument.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == minimalZipOriginalFilename,
        resourceType.toString == OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation,
        savedDocument.internalFilename == uploadedFile.internalFilename,
        savedDocument.url.nonEmpty,
      )
    },
    test("change a Zip file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTestZip, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.ArchiveFile,
                           resourceIri = zipResourceIri.get,
                           valueIri = zipValueIri.get,
                           internalFilename = uploadedFile.internalFilename,
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = zipValueIri.set(resIri.toString)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${zipResourceIri.get}").flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValue = getValueFromResource(
                       resource = resource,
                       propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
                       expectedValueIri = zipValueIri.get,
                     )

        savedDocument = savedValueToSavedDocument(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedDocument.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == testZipOriginalFilename,
        savedDocument.internalFilename == uploadedFile.internalFilename,
        savedDocument.url.nonEmpty,
      )
    },
    test("create a resource of type ArchiveRepresentation with a 7z file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTest7z, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri.map(_.toIri)
        _       = zipResourceIri.set(resIri)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${zipResourceIri.get}").flatMap(_.assert200)

        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues = getValuesFromResource(
                        resource = resource,
                        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
                      )

        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = zipValueIri.set(valueIri.toString)

        savedDocument = savedValueToSavedDocument(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedDocument.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == test7zOriginalFilename,
        resourceType.toString == OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation,
        savedDocument.internalFilename == uploadedFile.internalFilename,
        savedDocument.url.nonEmpty,
      )
    },
    test("create a resource with a WAV file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToMinimalWav, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.AudioFile, internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri.map(_.toIri)
        _       = wavResourceIri.set(resIri)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${wavResourceIri.get}").flatMap(_.assert200)

        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues = getValuesFromResource(
                        resource = resource,
                        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
                      )

        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = wavValueIri.set(valueIri.toString)

        savedAudioFile = savedValueToSavedAudioFile(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedAudioFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == minimalWavOriginalFilename,
        resourceType.toString == "http://api.knora.org/ontology/knora-api/v2#AudioRepresentation",
        savedAudioFile.internalFilename == uploadedFile.internalFilename,
        savedAudioFile.url.nonEmpty,
      )
    },
    test("change a WAV file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTestWav, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.AudioFile,
                           resourceIri = wavResourceIri.get,
                           internalFilename = uploadedFile.internalFilename,
                           valueIri = wavValueIri.get,
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        resIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _       = wavValueIri.set(resIri.toIri)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${wavResourceIri.get}").flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValue = getValueFromResource(
                       resource = resource,
                       propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
                       expectedValueIri = wavValueIri.get,
                     )

        savedAudioFile = savedValueToSavedAudioFile(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedAudioFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == testWavOriginalFilename,
        savedAudioFile.internalFilename == uploadedFile.internalFilename,
        savedAudioFile.url.nonEmpty,
      )
    },
    test("create a resource with a video file") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTestVideo, anythingShortcode)

        // Create the resource in the API.
        jsonLdEntity = UploadFileRequest
                         .make(fileType = FileType.MovingImageFile(), internalFilename = uploadedFile.internalFilename)
                         .toJsonLd()

        response <- TestApiClient
                      .postJsonLd(uri"/v2/resources", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        dataIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _        = videoResourceIri.set(dataIri.toString)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${videoResourceIri.get}").flatMap(_.assert200)

        resourceType <- resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri

        // Get the new file value from the resource.
        savedValues = getValuesFromResource(
                        resource = resource,
                        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
                      )

        savedValueObj = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
        valueIri     <- savedValueObj.getRequiredIdValueAsKnoraDataIri
        _             = videoValueIri.set(valueIri.toString)

        savedVideoFile = savedValueToSavedVideoFile(savedValueObj)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedVideoFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == testVideoOriginalFilename,
        resourceType.toString == "http://api.knora.org/ontology/knora-api/v2#MovingImageRepresentation",
        savedVideoFile.internalFilename == uploadedFile.internalFilename,
        savedVideoFile.url.nonEmpty,
      )
    },
    test("change a video file value") {
      for {
        // Upload the file to Sipi.
        uploadedFile <- TestDspIngestClient.uploadFile(pathToTestVideo2, anythingShortcode)

        // Update the value.
        jsonLdEntity = ChangeFileRequest
                         .make(
                           fileType = FileType.MovingImageFile(),
                           resourceIri = videoResourceIri.get,
                           internalFilename = uploadedFile.internalFilename,
                           valueIri = videoValueIri.get,
                         )
                         .toJsonLd

        response <- TestApiClient
                      .putJsonLd(uri"/v2/values", jsonLdEntity, anythingAdminUser)
                      .flatMap(_.assert200)
                      .mapAttempt(JsonLDUtil.parseJsonLD)
        dataIri <- response.body.getRequiredIdValueAsKnoraDataIri
        _        = videoValueIri.set(dataIri.toString)

        resource <- TestApiClient.getJsonLdDocument(uri"/v2/resources/${videoResourceIri.get}").flatMap(_.assert200)

        // Get the new file value from the resource.
        savedValue = getValueFromResource(
                       resource = resource,
                       propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
                       expectedValueIri = videoValueIri.get,
                     )

        savedVideoFile = savedValueToSavedVideoFile(savedValue)

        // Request the permanently stored file from Sipi.
        _ <- TestSipiApiClient.getFile(savedVideoFile.url).flatMap(_.assert200)
      } yield assertTrue(
        uploadedFile.originalFilename == testVideo2OriginalFilename,
        savedVideoFile.internalFilename == uploadedFile.internalFilename,
        savedVideoFile.url.nonEmpty,
      )
    },
  )
}
