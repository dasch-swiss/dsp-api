/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import java.net.URLEncoder
import java.nio.file.{Files, Paths}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol._
import org.knora.webapi.models.filemodels._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestIri

import scala.concurrent.Await
import scala.concurrent.duration._

object KnoraSipiIntegrationV2ITSpec {
  val config: Config = ConfigFactory.parseString("""
                                                   |akka.loglevel = "DEBUG"
                                                   |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
class KnoraSipiIntegrationV2ITSpec
    extends ITKnoraLiveSpec(KnoraSipiIntegrationV2ITSpec.config)
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val anythingUserEmail   = SharedTestDataADM.anythingAdminUser.email
  private val incunabulaUserEmail = SharedTestDataADM.incunabulaMemberUser.email
  private val password            = SharedTestDataADM.testPass

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
  private val pathToMarbles           = Paths.get("..", s"test_data/test_route/images/$marblesOriginalFilename")
  private val marblesWidth            = 1419
  private val marblesHeight           = 1001

  private val pathToMarblesWithWrongExtension =
    Paths.get("..", "test_data/test_route/images/marbles_with_wrong_extension.jpg")

  private val trp88OriginalFilename = "Trp88.tiff"
  private val pathToTrp88           = Paths.get("..", s"test_data/test_route/images/$trp88OriginalFilename")
  private val trp88Width            = 499
  private val trp88Height           = 630

  private val minimalPdfOriginalFilename = "minimal.pdf"
  private val pathToMinimalPdf           = Paths.get("..", s"test_data/test_route/files/$minimalPdfOriginalFilename")
  private val minimalPdfWidth            = 1250
  private val minimalPdfHeight           = 600

  private val testPdfOriginalFilename = "test.pdf"
  private val pathToTestPdf           = Paths.get("..", s"test_data/test_route/files/$testPdfOriginalFilename")
  private val testPdfWidth            = 2480
  private val testPdfHeight           = 3508

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
  private val minimalWavDuration         = BigDecimal("0.0")

  private val testWavOriginalFilename = "test.wav"
  private val pathToTestWav           = Paths.get("..", s"test_data/test_route/files/$testWavOriginalFilename")

  private val testVideoOriginalFilename = "testVideo.mp4"
  private val pathToTestVideo           = Paths.get("..", s"test_data/test_route/files/$testVideoOriginalFilename")

  private val testVideo2OriginalFilename = "testVideo2.mp4"
  private val pathToTestVideo2           = Paths.get("..", s"test_data/test_route/files/$testVideo2OriginalFilename")

  private val thingDocumentIRI = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingDocument"

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
   * @param pageCount        the document's page count.
   * @param width            the document's width in pixels.
   * @param height           the document's height in pixels.
   */
  case class SavedDocument(
    internalFilename: String,
    url: String,
    pageCount: Option[Int],
    width: Option[Int],
    height: Option[Int]
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
   * @param duration         the duration of the audio in seconds.
   */
  case class SavedAudioFile(internalFilename: String, url: String, duration: Option[BigDecimal])

  /**
   * Represents the information that Knora returns about a video file value that was created.
   *
   * @param internalFilename the file's internal filename.
   * @param url              the file's URL.
   * @param width            the video's width in pixels.
   * @param height           the video's height in pixels.
   * @param duration         the duration of the video in seconds.
   * @param fps              the frame rate of the video in seconds.
   */
  case class SavedVideoFile(
    internalFilename: String,
    url: String
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
    resource.requireArray(propertyIriInResult.toString)

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
    expectedValueIri: IRI
  ): JsonLDObject = {
    val resourceIri: IRI = resource.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
    val propertyValues: JsonLDArray =
      getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

    val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
      case jsonLDObject: JsonLDObject
          if jsonLDObject.requireStringWithValidation(
            JsonLDKeywords.ID,
            stringFormatter.validateAndEscapeIri
          ) == expectedValueIri =>
        jsonLDObject
    }

    if (matchingValues.isEmpty) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>"
      )
    }

    if (matchingValues.size > 1) {
      throw AssertionException(
        s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>"
      )
    }

    matchingValues.head
  }

  /**
   * Given a JSON-LD object representing a Knora image file value, returns a [[SavedImage]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora image file value.
   * @return a [[SavedImage]] containing the same information.
   */
  private def savedValueToSavedImage(savedValue: JsonLDObject): SavedImage = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val iiifUrl = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    val width  = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimX)
    val height = savedValue.requireInt(OntologyConstants.KnoraApiV2Complex.StillImageFileValueHasDimY)

    SavedImage(
      internalFilename = internalFilename,
      iiifUrl = iiifUrl,
      width = width,
      height = height
    )
  }

  /**
   * Given a JSON-LD object representing a Knora document file value, returns a [[SavedDocument]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedDocument]] containing the same information.
   */
  private def savedValueToSavedDocument(savedValue: JsonLDObject): SavedDocument = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    val pageCount: Option[Int] = savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasPageCount)
    val dimX: Option[Int]      = savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimX)
    val dimY: Option[Int]      = savedValue.maybeInt(OntologyConstants.KnoraApiV2Complex.DocumentFileValueHasDimY)

    SavedDocument(
      internalFilename = internalFilename,
      url = url,
      pageCount = pageCount,
      width = dimX,
      height = dimY
    )
  }

  /**
   * Given a JSON-LD object representing a Knora text file value, returns a [[SavedTextFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora document file value.
   * @return a [[SavedTextFile]] containing the same information.
   */
  private def savedValueToSavedTextFile(savedValue: JsonLDObject): SavedTextFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    SavedTextFile(
      internalFilename = internalFilename,
      url = url
    )
  }

  /**
   * Given a JSON-LD object representing a Knora audio file value, returns a [[SavedAudioFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora audio file value.
   * @return a [[SavedAudioFile]] containing the same information.
   */
  private def savedValueToSavedAudioFile(savedValue: JsonLDObject): SavedAudioFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    val duration: Option[BigDecimal] = savedValue.maybeDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.AudioFileValueHasDuration,
      expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
      validationFun = stringFormatter.validateBigDecimal
    )

    SavedAudioFile(
      internalFilename = internalFilename,
      url = url,
      duration = duration
    )
  }

  /**
   * Given a JSON-LD object representing a Knora video file value, returns a [[SavedVideoFile]] containing the same information.
   *
   * @param savedValue a JSON-LD object representing a Knora video file value.
   * @return a [[SavedVideoFile]] containing the same information.
   */
  private def savedValueToSavedVideoFile(savedValue: JsonLDObject): SavedVideoFile = {
    val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2Complex.FileValueHasFilename)

    val url: String = savedValue.requireDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.FileValueAsUrl,
      expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
      validationFun = stringFormatter.toSparqlEncodedString
    )

    val duration: Option[BigDecimal] = savedValue.maybeDatatypeValueInObject(
      key = OntologyConstants.KnoraApiV2Complex.AudioFileValueHasDuration,
      expectedDatatype = OntologyConstants.Xsd.Decimal.toSmartIri,
      validationFun = stringFormatter.validateBigDecimal
    )

    SavedVideoFile(
      internalFilename = internalFilename,
      url = url
    )
  }

  "The Knora/Sipi integration" should {
    var loginToken: String = ""

    "not accept a token in Sipi that hasn't been signed by Knora" in {
      val invalidToken =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NDM1MTEyMiwiaWF0IjoxNTQxNzU5MTIyLCJqdGkiOiJ4bnlYeklFb1QxNmM2dkpDbHhSQllnIn0.P2Aq37G6XMLLBVMdnpDVVhWjenbVw0HTb1BpEuTWGRo"

      // The image to be uploaded.
      assert(Files.exists(pathToMarbles), s"File $pathToMarbles does not exist")

      // A multipart/form-data request containing the image.
      val sipiFormData = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "file",
          HttpEntity.fromPath(MediaTypes.`image/tiff`, pathToMarbles),
          Map("filename" -> pathToMarbles.getFileName.toString)
        )
      )

      // Send a POST request to Sipi, asking it to convert the image to JPEG 2000 and store it in a temporary file.
      val sipiRequest  = Post(s"$baseInternalSipiUrl/upload?token=$invalidToken", sipiFormData)
      val sipiResponse = singleAwaitingRequest(sipiRequest)
      assert(sipiResponse.status == StatusCodes.Unauthorized)
    }

    "log in as a Knora user" in {
      /* Correct username and correct password */

      val params =
        s"""
           |{
           |    "email": "$anythingUserEmail",
           |    "password": "$password"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
      loginToken = lr.token

      loginToken.nonEmpty should be(true)

      logger.debug("token: {}", loginToken)
    }

    "create a resource with a still image file" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
        )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(marblesOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.StillImageFile(),
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd(
          className = Some("ThingPicture"),
          ontologyName = "anything"
        )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      stillImageResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.requireTypeAsKnoraTypeIri.toString == "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      stillImageFileValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedImage = savedValueToSavedImage(savedValueObj)
      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == marblesWidth)
      assert(savedImage.height == marblesHeight)
    }

    "reject an image file with the wrong file extension" in {
      val exception = intercept[AssertionException] {
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(FileToUpload(path = pathToMarblesWithWrongExtension, mimeType = MediaTypes.`image/tiff`))
        )
      }

      assert(exception.getMessage.contains("MIME type and/or file extension are inconsistent"))
    }

    "change a still image file value" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse =
        uploadToSipi(
          loginToken = loginToken,
          filesToUpload = Seq(FileToUpload(path = pathToTrp88, mimeType = MediaTypes.`image/tiff`))
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
          ontologyName = "anything"
        )
        .toJsonLd

      // Send the JSON in a PUT request to the API.
      val knoraPostRequest =
        Put(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc = getResponseJsonLD(knoraPostRequest)
      stillImageFileValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(stillImageResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri,
        expectedValueIri = stillImageFileValueIri.get
      )

      val savedImage = savedValueToSavedImage(savedValue)
      assert(savedImage.internalFilename == uploadedFile.internalFilename)
      assert(savedImage.width == trp88Width)
      assert(savedImage.height == trp88Height)

      // Request the permanently stored image from Sipi.
      val sipiGetImageRequest = Get(savedImage.iiifUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetImageRequest)
    }

    "delete the temporary file if Knora rejects the request to create a file value" in {
      // Upload the image to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
      )

      val internalFilename = sipiUploadResponse.uploadedFiles.head.internalFilename
      val temporaryUrl =
        sipiUploadResponse.uploadedFiles.head.temporaryUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl)
      val temporaryDirectDownloadUrl = temporaryUrl + "/file"

      // JSON describing the new image to Knora.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.StillImageFile(),
          internalFilename = internalFilename,
          resourceIri = stillImageResourceIri.get,
          valueIri = stillImageFileValueIri.get,
          className = Some("ThingDocument"), // refuse, as it should be "ThingImage"
          ontologyName = "anything"
        )
        .toJsonLd

      // Send the JSON in a POST request to Knora.
      val knoraPostRequest =
        Post(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(incunabulaUserEmail, password)
        )
      val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
      assert(knoraPostResponse.status == StatusCodes.Forbidden)

      // Request the temporary image from Sipi.
      val sipiGetTmpFileRequest = Get(temporaryDirectDownloadUrl)
      val sipiResponse          = singleAwaitingRequest(sipiGetTmpFileRequest)
      assert(sipiResponse.status == StatusCodes.NotFound)
    }

    "create a resource with a PDF file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToMinimalPdf, mimeType = MediaTypes.`application/pdf`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalPdfOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity =
        UploadFileRequest
          .make(
            fileType = FileType.DocumentFile(),
            internalFilename = uploadedFile.internalFilename
          )
          .toJsonLd(
            className = Some("ThingDocument"),
            ontologyName = "anything"
          )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      pdfResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(resource.requireTypeAsKnoraTypeIri.toString == thingDocumentIRI)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      pdfValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)
      assert(savedDocument.pageCount.contains(1))
      assert(savedDocument.width.contains(minimalPdfWidth))
      assert(savedDocument.height.contains(minimalPdfHeight))

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a PDF file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToTestPdf, mimeType = MediaTypes.`application/pdf`))
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
          ontologyName = "anything"
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      pdfValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(pdfResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasDocumentFileValue.toSmartIri,
        expectedValueIri = pdfValueIri.get
      )

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)
      assert(savedDocument.pageCount.contains(1))
      assert(savedDocument.width.contains(testPdfWidth))
      assert(savedDocument.height.contains(testPdfHeight))

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "not create a document resource if the file is actually a zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToMinimalZip, mimeType = MediaTypes.`application/zip`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)
      uploadedFile.fileType should equal("archive")

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.DocumentFile(),
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd(
          className = Some("ThingDocument"),
          ontologyName = "anything"
        )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource with a CSV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToCsv1, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      csvResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      csvValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

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
          Seq(FileToUpload(path = pathToCsv2, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv2OriginalFilename)

      // Update the value.
      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = csvResourceIri.get,
          valueIri = csvValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      csvValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(csvResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
        expectedValueIri = csvValueIri.get
      )

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
          Seq(FileToUpload(path = pathToCsv1, mimeType = MediaTypes.`text/csv`.toContentType(HttpCharsets.`UTF-8`)))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(csv1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.StillImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource with an XML file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload =
          Seq(FileToUpload(path = pathToXml1, mimeType = MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`)))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml1OriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      xmlResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      xmlValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

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
        filesToUpload =
          Seq(FileToUpload(path = pathToXml2, mimeType = MediaTypes.`text/xml`.toContentType(HttpCharsets.`UTF-8`)))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(xml2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.TextFile,
          internalFilename = uploadedFile.internalFilename,
          resourceIri = xmlResourceIri.get,
          valueIri = xmlValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      xmlValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(xmlResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasTextFileValue.toSmartIri,
        expectedValueIri = xmlValueIri.get
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
        filesToUpload = Seq(FileToUpload(path = pathToMinimalZip, mimeType = MediaTypes.`application/zip`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.TextFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest)
    }

    "create a resource of type ArchiveRepresentation with a Zip file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToMinimalZip, mimeType = MediaTypes.`application/zip`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalZipOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)

      resource.requireTypeAsKnoraTypeIri.toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      zipValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)
      assert(savedDocument.pageCount.isEmpty)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a Zip file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToTestZip, mimeType = MediaTypes.`application/zip`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testZipOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.ArchiveFile,
          resourceIri = zipResourceIri.get,
          valueIri = zipValueIri.get,
          internalFilename = uploadedFile.internalFilename
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri,
        expectedValueIri = zipValueIri.get
      )

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValue)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)
      assert(savedDocument.pageCount.isEmpty)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource of type ArchiveRepresentation with a 7z file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToTest7z, mimeType = MediaTypes.`application/x-7z-compressed`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(test7zOriginalFilename)

      // Create the resource in the API.

      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.ArchiveFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      zipResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(zipResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)

      resource.requireTypeAsKnoraTypeIri.toString should equal(
        OntologyConstants.KnoraApiV2Complex.ArchiveRepresentation
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasArchiveFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      zipValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedDocument: SavedDocument = savedValueToSavedDocument(savedValueObj)
      assert(savedDocument.internalFilename == uploadedFile.internalFilename)
      assert(savedDocument.pageCount.isEmpty)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedDocument.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "create a resource with a WAV file" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToMinimalWav, mimeType = MediaTypes.`audio/wav`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(minimalWavOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.AudioFile, internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      wavResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(wavResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.requireTypeAsKnoraTypeIri.toString == "http://api.knora.org/ontology/knora-api/v2#AudioRepresentation"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      wavValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

      val savedAudioFile: SavedAudioFile = savedValueToSavedAudioFile(savedValueObj)
      assert(savedAudioFile.internalFilename == uploadedFile.internalFilename)
      assert(savedAudioFile.duration.forall(_ == minimalWavDuration))

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedAudioFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }

    "change a WAV file value" in {
      // Upload the file to Sipi.
      val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
        loginToken = loginToken,
        filesToUpload = Seq(FileToUpload(path = pathToTestWav, mimeType = MediaTypes.`audio/wav`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testWavOriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.AudioFile,
          resourceIri = wavResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = wavValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      wavValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(wavResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasAudioFileValue.toSmartIri,
        expectedValueIri = wavValueIri.get
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
        filesToUpload = Seq(FileToUpload(path = pathToTestVideo, mimeType = MediaTypes.`video/mp4`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideoOriginalFilename)

      // Create the resource in the API.
      val jsonLdEntity = UploadFileRequest
        .make(fileType = FileType.MovingImageFile(), internalFilename = uploadedFile.internalFilename)
        .toJsonLd()

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      videoResourceIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest          = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(videoResourceIri.get, "UTF-8")}")
      val resource: JsonLDDocument = getResponseJsonLD(knoraGetRequest)
      assert(
        resource.requireTypeAsKnoraTypeIri.toString == "http://api.knora.org/ontology/knora-api/v2#MovingImageRepresentation"
      )

      // Get the new file value from the resource.

      val savedValues: JsonLDArray = getValuesFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri
      )

      val savedValue: JsonLDValue = if (savedValues.value.size == 1) {
        savedValues.value.head
      } else {
        throw AssertionException(s"Expected one file value, got ${savedValues.value.size}")
      }

      val savedValueObj: JsonLDObject = savedValue match {
        case jsonLDObject: JsonLDObject => jsonLDObject
        case other                      => throw AssertionException(s"Invalid value object: $other")
      }

      videoValueIri.set(savedValueObj.requireIDAsKnoraDataIri.toString)

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
        filesToUpload = Seq(FileToUpload(path = pathToTestVideo2, mimeType = MediaTypes.`video/mp4`))
      )

      val uploadedFile: SipiUploadResponseEntry = sipiUploadResponse.uploadedFiles.head
      uploadedFile.originalFilename should ===(testVideo2OriginalFilename)

      // Update the value.

      val jsonLdEntity = ChangeFileRequest
        .make(
          fileType = FileType.MovingImageFile(),
          resourceIri = videoResourceIri.get,
          internalFilename = uploadedFile.internalFilename,
          valueIri = videoValueIri.get
        )
        .toJsonLd

      val request =
        Put(s"$baseApiUrl/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
      videoValueIri.set(responseJsonDoc.body.requireIDAsKnoraDataIri.toString)

      // Get the resource from Knora.
      val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(videoResourceIri.get, "UTF-8")}")
      val resource        = getResponseJsonLD(knoraGetRequest)

      // Get the new file value from the resource.
      val savedValue: JsonLDObject = getValueFromResource(
        resource = resource,
        propertyIriInResult = OntologyConstants.KnoraApiV2Complex.HasMovingImageFileValue.toSmartIri,
        expectedValueIri = videoValueIri.get
      )

      val savedVideoFile: SavedVideoFile = savedValueToSavedVideoFile(savedValue)
      assert(savedVideoFile.internalFilename == uploadedFile.internalFilename)

      // Request the permanently stored file from Sipi.
      val sipiGetFileRequest = Get(savedVideoFile.url.replace("http://0.0.0.0:1024", baseInternalSipiUrl))
      checkResponseOK(sipiGetFileRequest)
    }
  }
}
