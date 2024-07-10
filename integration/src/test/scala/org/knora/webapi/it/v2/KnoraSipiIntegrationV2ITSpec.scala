/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.Await
import scala.concurrent.duration.*

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.AuthenticationV2JsonProtocol
import org.knora.webapi.e2e.v2.LoginResponse
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.models.filemodels.*
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.util.MutableTestIri

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
class KnoraSipiIntegrationV2ITSpec
    extends ITKnoraLiveSpec
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUserEmail = SharedTestDataADM.anythingAdminUser.email
  private val password          = SharedTestDataADM.testPass

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

  private val jsonLdHttpEntity = HttpEntity(RdfMediaTypes.`application/ld+json`, _: String)
  private val addAuthorization = addCredentials(BasicHttpCredentials(anythingUserEmail, password))
  private val addAssetIngested = addHeader("X-Asset-Ingested", "true")
  private val encodeUTF8       = URLEncoder.encode(_: String, "UTF-8")

  private val marblesOriginalFilename = "marbles.tif"
  private val pathToMarbles           = Paths.get("..", s"test_data/test_route/images/$marblesOriginalFilename")
  private val marblesWidth            = 1419
  private val marblesHeight           = 1001

  private val pathToMarblesWithWrongExtension =
    Paths.get("..", "test_data/test_route/images/marbles_with_wrong_extension.jpg")

  private val jp2OriginalFilename = "67352ccc-d1b0-11e1-89ae-279075081939.jp2"
  private val pathToJp2           = Paths.get("..", s"test_data/test_route/images/$jp2OriginalFilename")

  private val trp88OriginalFilename = "Trp88.tiff"
  private val pathToTrp88           = Paths.get("..", s"test_data/test_route/images/$trp88OriginalFilename")
  private val trp88Width            = 499
  private val trp88Height           = 630

  private val minimalPdfOriginalFilename = "minimal.pdf"
  private val pathToMinimalPdf           = Paths.get("..", s"test_data/test_route/files/$minimalPdfOriginalFilename")

  private val testPdfOriginalFilename = "test.pdf"
  private val pathToTestPdf           = Paths.get("..", s"test_data/test_route/files/$testPdfOriginalFilename")

  private val csv1OriginalFilename = "eggs.csv"
  private val pathToCsv1           = Paths.get("..", s"test_data/test_route/files/$csv1OriginalFilename")

  private val csv2OriginalFilename = "spam.csv"
  private val pathToCsv2           = Paths.get("..", s"test_data/test_route/files/$csv2OriginalFilename")

  private val xml1OriginalFilename = "test1.xml"
  private val pathToXml1           = Paths.get("..", s"test_data/test_route/files/$xml1OriginalFilename")

  private val xml2OriginalFilename = "test2.xml"
  private val pathToXml2           = Paths.get("..", s"test_data/test_route/files/$xml2OriginalFilename")

  private val minimalZipOriginalFilename = "minimal.zip"
  private val pathToMinimalZip           = Paths.get("..", s"test_data/test_route/files/$minimalZipOriginalFilename")

  private val testZipOriginalFilename = "test.zip"
  private val pathToTestZip           = Paths.get("..", s"test_data/test_route/files/$testZipOriginalFilename")

  private val test7zOriginalFilename = "test.7z"
  private val pathToTest7z           = Paths.get("..", s"test_data/test_route/files/$test7zOriginalFilename")

  private val minimalWavOriginalFilename = "minimal.wav"
  private val pathToMinimalWav           = Paths.get("..", s"test_data/test_route/files/$minimalWavOriginalFilename")

  private val testWavOriginalFilename = "test.wav"
  private val pathToTestWav           = Paths.get("..", s"test_data/test_route/files/$testWavOriginalFilename")

  private val testVideoOriginalFilename = "testVideo.mp4"
  private val pathToTestVideo           = Paths.get("..", s"test_data/test_route/files/$testVideoOriginalFilename")

  private val testVideo2OriginalFilename = "testVideo2.mp4"
  private val pathToTestVideo2           = Paths.get("..", s"test_data/test_route/files/$testVideo2OriginalFilename")

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
  case class SavedDocument(
    internalFilename: String,
    url: String,
  )

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
  case class SavedVideoFile(
    internalFilename: String,
    url: String,
  )

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

  protected def requestJsonLDWithAuth(request: HttpRequest): JsonLDDocument =
    getResponseJsonLD(request ~> addAuthorization)

  "The Knora/Sipi integration" should {
    var loginToken: String = ""

    "log in as a Knora user" in {
      /* Correct username and correct password */
      val params   = s"""{"email": "$anythingUserEmail", "password": "$password"}"""
      val request  = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      loginToken = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds).token
      loginToken.nonEmpty should be(true)
    }

    "create a resource with a still image file" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload =
            Seq(FileToUpload(path = pathToMarbles, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF)),
        )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(marblesOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(FileType.StillImageFile(), uploadedFile.internalFilename)
        .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      stillImageResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(stillImageResourceIri.get)}"))
      assert(
        UnsafeZioRun
          .runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri)
          .toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture",
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray    = getValuesFromResource(resource, HasStillImageFileValue.toSmartIri)
      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      stillImageFileValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedImage = savedValueToSavedImage(savedValueObj)
      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == marblesWidth)
      assert(savedImage.height == marblesHeight)
    }

    "create a resource with a still image file without processing" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadWithoutProcessingResponse =
        uploadWithoutProcessingToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(FileToUpload(path = pathToJp2, mimeType = org.apache.http.entity.ContentType.IMAGE_JPEG)),
        )

      val uploadedFile: SipiUploadWithoutProcessingResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.filename should ===(jp2OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = uploadedFile.filename)
        .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")

      val responseJsonDoc = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      stillImageResourceIri.set(UnsafeZioRun.runOrThrow(responseJsonDoc.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(stillImageResourceIri.get)}"))
      assert(
        UnsafeZioRun
          .runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri)
          .toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture",
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray    = getValuesFromResource(resource, HasStillImageFileValue.toSmartIri)
      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      stillImageFileValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedImage = savedValueToSavedImage(savedValueObj)
      assert(savedImage.internalFilename == uploadedFile.filename)
    }

    "create a resource with a still image file that has already been ingested" in {
      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = "De6XyNL4H71-D9QxghOuOPJ.jp2")
        .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")
      val response = requestJsonLDWithAuth(
        Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)) ~> addAssetIngested,
      )
      val resIri     = UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString
      val getRequest = Get(s"$baseApiUrl/v2/resources/${encodeUTF8(resIri)}")
      checkResponseOK(getRequest)
    }

    "not create a resource with a still image file that has already been ingested if the header is not provided" in {
      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = "De6XyNL4H71-D9QxghOuOPJ.jp2")
        .toJsonLd(className = Some("ThingPicture"), ontologyName = "anything")
      // no X-Asset-Ingested header
      val request = Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)) ~> addAuthorization
      assert(singleAwaitingRequest(request).status == StatusCodes.BadRequest)
    }

    "reject an image file with the wrong file extension" in {
      val exception = intercept[BadRequestException] {
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(
            FileToUpload(
              path = pathToMarblesWithWrongExtension,
              mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF,
            ),
          ),
        )
      }

      assert(exception.getMessage.contains("MIME type and/or file extension are inconsistent"))
    }

    "change a still image file value" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload =
            Seq(FileToUpload(path = pathToTrp88, mimeType = org.apache.http.entity.ContentType.IMAGE_TIFF)),
        )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(trp88OriginalFilename)

      // JSON describing the new image to the API.
      val jsonLdEntity = ChangeFileRequest
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
      val response = requestJsonLDWithAuth(Put(baseApiUrl + "/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      stillImageFileValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource   = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(stillImageResourceIri.get)}"))
      val savedValue = getValueFromResource(resource, HasStillImageFileValue.toSmartIri, stillImageFileValueIri.get)
      val savedImage = savedValueToSavedImage(savedValue)

      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == trp88Width)
      assert(savedImage.height == trp88Height)

      // Request the permanently stored image from Sipi.
      val sipiGetImageRequest = Get(savedImage.iiifUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetImageRequest)
    }

    "create a resource with a PDF file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalPdfOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity =
        UploadFileRequest
          .make(fileType = FileType.DocumentFile(), internalFilename = uploadedFile.internalFilename)
          .toJsonLd(className = Some("ThingDocument"), ontologyName = "anything")

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      pdfResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(pdfResourceIri.get)}"))
      assert(
        UnsafeZioRun.runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri).toString == thingDocumentIRI,
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray    = getValuesFromResource(resource, HasDocumentFileValue.toSmartIri)
      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      pdfValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a PDF file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestPdf, mimeType = org.apache.http.entity.ContentType.create("application/pdf")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testPdfOriginalFilename)

      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.DocumentFile(),
          internalFilename = uploadedFile.internalFilename,
          resourceIri = pdfResourceIri.get,
          valueIri = pdfValueIri.get,
          className = Some("ThingDocument"),
          ontologyName = "anything",
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      pdfValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(pdfResourceIri.get)}"))

      val savedValue: JsonLDObject     = getValueFromResource(resource, HasDocumentFileValue.toSmartIri, pdfValueIri.get)
      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a PDF file value with X-Asset-Ingested=true" in {
      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.DocumentFile(),
          internalFilename = "De6XyNL4H71-D9QxghOuOPJ.jp2",
          resourceIri = pdfResourceIri.get,
          valueIri = pdfValueIri.get,
          className = Some("ThingDocument"),
          ontologyName = "anything",
        )
        .toJsonLd

      val response: JsonLDDocument = requestJsonLDWithAuth(
        Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)) ~> addAssetIngested,
      )

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(pdfResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedDocument: SavedDocument = savedValueToSavedDocument(
        getValueFromResource(
          resource = resource,
          propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri,
          expectedValueIri = UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString,
        ),
      )
      assert(savedDocument.internalFilename == "De6XyNL4H71-D9QxghOuOPJ.jp2")
    }

    "not create a document resource if the file is actually a zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)
      uploadedFile.fileType should equal("archive")

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.DocumentFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd(className = Some("ThingDocument"), ontologyName = "anything")

      val request = Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)) ~> addAuthorization
      assert(singleAwaitingRequest(request).status == StatusCodes.BadRequest)
    }

    "create a resource with a CSV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv1, mimeType = org.apache.http.entity.ContentType.create("text/csv"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response    = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      val resourceIri = response.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      csvResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      // Get the new file value from the resource.
      val resource                    = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(resourceIri)}"))
      val savedValues: JsonLDArray    = getValuesFromResource(resource, HasTextFileValue.toSmartIri)
      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      csvValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a CSV file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv2, mimeType = org.apache.http.entity.ContentType.create("text/csv"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv2OriginalFilename)

      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = csvResourceIri.get,
          valueIri = csvValueIri.get,
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      csvValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(csvResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedValue: JsonLDObject     = getValueFromResource(resource, HasTextFileValue.toSmartIri, csvValueIri.get)
      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a resource with a still image file that's actually a text file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv1, mimeType = org.apache.http.entity.ContentType.create("text/csv"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request  = Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)) ~> addAuthorization
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource with an XML file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToXml1, mimeType = org.apache.http.entity.ContentType.TEXT_XML)),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response         = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      val resourceIri: IRI = response.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      xmlResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(resourceIri)}"))

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
      )

      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      xmlValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValueObj)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change an XML file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToXml2, mimeType = org.apache.http.entity.ContentType.TEXT_XML)),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = xmlResourceIri.get,
          valueIri = xmlValueIri.get,
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      xmlValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(xmlResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
        expectedValueIri = xmlValueIri.get,
      )

      val savedTextFile: SavedTextFile = savedValueToSavedTextFile(savedValue)
      assert(savedTextFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedTextFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a resource of type TextRepresentation with a Zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request  = Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)) ~> addAuthorization
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource of type ArchiveRepresentation with a Zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToMinimalZip, mimeType = org.apache.http.entity.ContentType.create("application/zip")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      zipResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(zipResourceIri.get)}"))

      UnsafeZioRun.runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri).toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation,
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
      )

      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      zipValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a Zip file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(path = pathToTestZip, mimeType = org.apache.http.entity.ContentType.create("application/zip")),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testZipOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.ArchiveFile,
          resourceIri = zipResourceIri.get,
          valueIri = zipValueIri.get,
          internalFilename = uploadedFile.internalFilename,
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      zipValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(zipResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
        expectedValueIri = zipValueIri.get,
      )

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource of type ArchiveRepresentation with a 7z file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(
          FileToUpload(
            path = pathToTest7z,
            mimeType = org.apache.http.entity.ContentType.create("application/x-7z-compressed"),
          ),
        ),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(test7zOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      zipResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(zipResourceIri.get)}"))

      UnsafeZioRun.runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri).toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation,
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
      )

      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      zipValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource with a WAV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToMinimalWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalWavOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.AudioFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      wavResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(wavResourceIri.get)}"))
      assert(
        UnsafeZioRun
          .runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri)
          .toString == "http://api.knora.org/ontology/knora-api/v2#AudioRepresentation",
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
      )

      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      wavValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedAudioFile: SavedAudioFile = savedValueToSavedAudioFile(savedValueObj)
      assert(savedAudioFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedAudioFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a WAV file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestWav, mimeType = org.apache.http.entity.ContentType.create("audio/wav"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testWavOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.AudioFile,
          resourceIri = wavResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = wavValueIri.get,
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      wavValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(wavResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
        expectedValueIri = wavValueIri.get,
      )

      val savedAudioFile: SavedAudioFile = savedValueToSavedAudioFile(savedValue)
      assert(savedAudioFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedAudioFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource with a video file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo, mimeType = org.apache.http.entity.ContentType.create("video/mp4"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideoOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.MovingImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val response = requestJsonLDWithAuth(Post(s"$baseApiUrl/v2/resources", jsonLdHttpEntity(jsonLdEntity)))
      videoResourceIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(videoResourceIri.get)}"))
      assert(
        UnsafeZioRun
          .runOrThrow(resource.body.getRequiredTypeAsKnoraApiV2ComplexTypeIri)
          .toString == "http://api.knora.org/ontology/knora-api/v2#MovingImageRepresentation",
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
      )

      val savedValueObj: JsonLDObject = assertingUnique(savedValues.value).asOpt[JsonLDObject].get
      videoValueIri.set(UnsafeZioRun.runOrThrow(savedValueObj.getRequiredIdValueAsKnoraDataIri).toString)

      val savedVideoFile: SavedVideoFile = savedValueToSavedVideoFile(savedValueObj)
      assert(savedVideoFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedVideoFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a video file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToTestVideo2, mimeType = org.apache.http.entity.ContentType.create("video/mp4"))),
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideo2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.MovingImageFile(),
          resourceIri = videoResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = videoValueIri.get,
        )
        .toJsonLd

      val response = requestJsonLDWithAuth(Put(s"$baseApiUrl/v2/values", jsonLdHttpEntity(jsonLdEntity)))
      videoValueIri.set(UnsafeZioRun.runOrThrow(response.body.getRequiredIdValueAsKnoraDataIri).toString)

      val resource = getResponseJsonLD(Get(s"$baseApiUrl/v2/resources/${encodeUTF8(videoResourceIri.get)}"))

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
        expectedValueIri = videoValueIri.get,
      )

      val savedVideoFile: SavedVideoFile = savedValueToSavedVideoFile(savedValue)
      assert(savedVideoFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedVideoFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }
  }
}
