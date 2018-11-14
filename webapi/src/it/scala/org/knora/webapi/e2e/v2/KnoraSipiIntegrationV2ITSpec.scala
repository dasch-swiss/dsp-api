package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v2.routing.authenticationmessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.{JsonLDArray, JsonLDConstants, JsonLDDocument, JsonLDObject}
import org.knora.webapi.util.{MutableTestIri, SmartIri, StringFormatter}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object KnoraSipiIntegrationV2ITSpec {
    val config: Config = ConfigFactory.parseString(
        """
          |akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

class KnoraSipiIntegrationV2ITSpec extends ITKnoraLiveSpec(KnoraSipiIntegrationV2ITSpec.config) with AuthenticationV2JsonProtocol with TriplestoreJsonProtocol {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private val marblesOriginalFilename = "marbles.tif"
    private val pathToMarbles = s"_test_data/test_route/images/$marblesOriginalFilename"
    private val marblesWidth = 1419
    private val marblesHeight = 1001

    private val trp88OriginalFilename = "Trp88.tiff"
    private val pathToTrp88 = s"_test_data/test_route/images/$trp88OriginalFilename"
    private val trp88Width = 499
    private val trp88Height = 630

    private val anythingUserEmail = "anything.user01@example.org"
    private val incunabulaUserEmail = "test.user2@test.ch"
    private val password = "test"
    private val stillImageFileValueIri = new MutableTestIri
    private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

    /**
      * Represents a file to be uploaded to Sipi.
      *
      * @param path     the path of the file.
      * @param mimeType the MIME type of the file.
      */
    case class FileToUpload(path: String, mimeType: ContentType)

    /**
      * Represents an image file to be uploaded to Sipi.
      *
      * @param fileToUpload the file to be uploaded.
      * @param width        the image's width in pixels.
      * @param height       the image's height in pixels.
      */
    case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

    /**
      * Represents the information that Sipi returns about each file that has been uploaded.
      *
      * @param internalFilename Sipi's internal filename for the stored temporary file.
      * @param temporaryBaseIIIFUrl       the base URL at which the temporary file can be accessed.
      */
    case class SipiUploadResponseEntry(internalFilename: String, temporaryBaseIIIFUrl: String)

    /**
      * Represents Sipi's response to a file upload request.
      *
      * @param uploadedFiles the information about each file that was uploaded.
      */
    case class SipiUploadResponse(uploadedFiles: Seq[SipiUploadResponseEntry])

    object GetImageMetadataResponseV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
        implicit val sipiUploadResponseEntryFormat: RootJsonFormat[SipiUploadResponseEntry] = jsonFormat2(SipiUploadResponseEntry)
        implicit val sipiUploadResponseFormat: RootJsonFormat[SipiUploadResponse] = jsonFormat1(SipiUploadResponse)
    }

    import GetImageMetadataResponseV2JsonProtocol._

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
      * Uploads a file to Sipi and returns the information in Sipi's response.
      *
      * @param loginToken    the login token to be included in the request to Sipi.
      * @param filesToUpload the files to be uploaded.
      * @return a [[SipiUploadResponse]] representing Sipi's response.
      */
    private def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse = {
        // Make a multipart/form-data request containing the files.

        val formDataParts: Seq[Multipart.FormData.BodyPart] = filesToUpload.map {
            fileToUpload =>
                val fileToSend = new File(fileToUpload.path)
                assert(fileToSend.exists(), s"File ${fileToUpload.path} does not exist")

                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(fileToUpload.mimeType, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
        }

        val sipiFormData = Multipart.FormData(formDataParts: _*)

        // Send a POST request to Sipi, asking it to convert the image to JPEG 2000 and store it in a temporary file.
        val sipiRequest = Post(s"$baseSipiUrl/upload?token=$loginToken", sipiFormData)
        val sipiUploadResponseJson: JsObject = getResponseJson(sipiRequest)
        // println(sipiUploadResponseJson.prettyPrint)
        val sipiUploadResponse: SipiUploadResponse = sipiUploadResponseJson.convertTo[SipiUploadResponse]

        // Request the temporary image from Sipi.
        for (responseEntry <- sipiUploadResponse.uploadedFiles) {
            val sipiGetTmpFileRequest = Get(responseEntry.temporaryBaseIIIFUrl + "/full/full/0/default.jpg")
            checkResponseOK(sipiGetTmpFileRequest)
        }

        sipiUploadResponse
    }

    /**
      * Given a JSON-LD document representing a resource, returns a JSON-LD array containing the values of the specified
      * property.
      *
      * @param resource            the JSON-LD document.
      * @param propertyIriInResult the property IRI.
      * @return a JSON-LD array containing the values of the specified property.
      */
    private def getValuesFromResource(resource: JsonLDDocument,
                                      propertyIriInResult: SmartIri): JsonLDArray = {
        resource.requireArray(propertyIriInResult.toString)
    }

    /**
      * Given a JSON-LD document representing a resource, returns a JSON-LD object representing the expected single
      * value of the specified property.
      *
      * @param resource            the JSON-LD document.
      * @param propertyIriInResult the property IRI.
      * @param expectedValueIri    the IRI of the expected value.
      * @return a JSON-LD object representing the expected single value of the specified property.
      */
    private def getValueFromResource(resource: JsonLDDocument,
                                     propertyIriInResult: SmartIri,
                                     expectedValueIri: IRI): JsonLDObject = {
        val resourceIri: IRI = resource.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
        val propertyValues: JsonLDArray = getValuesFromResource(resource = resource, propertyIriInResult = propertyIriInResult)

        val matchingValues: Seq[JsonLDObject] = propertyValues.value.collect {
            case jsonLDObject: JsonLDObject if jsonLDObject.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri) == expectedValueIri => jsonLDObject
        }

        if (matchingValues.isEmpty) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> does not have value <$expectedValueIri>")
        }

        if (matchingValues.size > 1) {
            throw AssertionException(s"Property <$propertyIriInResult> of resource <$resourceIri> has more than one value with the IRI <$expectedValueIri>")
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
        val internalFilename = savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename)

        val iiifUrl = savedValue.requireDatatypeValueInObject(
            key = OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl,
            expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
            validationFun = stringFormatter.toSparqlEncodedString
        )

        val width = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX)
        val height = savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY)

        SavedImage(
            internalFilename = internalFilename,
            iiifUrl = iiifUrl,
            width = width,
            height = height
        )
    }

    "The Knora/Sipi integration" should {
        var loginToken: String = ""

        "not accept a token in Sipi that hasn't been signed by Knora" in {
            val invalidToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJLbm9yYSIsInN1YiI6Imh0dHA6Ly9yZGZoLmNoL3VzZXJzLzlYQkNyRFYzU1JhN2tTMVd3eW5CNFEiLCJhdWQiOlsiS25vcmEiLCJTaXBpIl0sImV4cCI6NDY5NDM1MTEyMiwiaWF0IjoxNTQxNzU5MTIyLCJqdGkiOiJ4bnlYeklFb1QxNmM2dkpDbHhSQllnIn0.P2Aq37G6XMLLBVMdnpDVVhWjenbVw0HTb1BpEuTWGRo"

            // The image to be uploaded.
            val fileToSend = new File(pathToMarbles)
            assert(fileToSend.exists(), s"File $pathToMarbles does not exist")

            // A multipart/form-data request containing the image.
            val sipiFormData = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "file",
                    HttpEntity.fromPath(MediaTypes.`image/tiff`, fileToSend.toPath),
                    Map("filename" -> fileToSend.getName)
                )
            )

            // Send a POST request to Sipi, asking it to convert the image to JPEG 2000 and store it in a temporary file.
            val sipiRequest = Post(s"$baseSipiUrl/upload?token=$invalidToken", sipiFormData)
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

            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
            loginToken = lr.token

            loginToken.nonEmpty should be(true)

            log.debug("token: {}", loginToken)
        }

        "create a still image file" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
            )

            val internalFilename = sipiUploadResponse.uploadedFiles.head.internalFilename
            val resourceIri: IRI = aThingPictureIri

            // JSON describing the new image to Knora.
            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            // Send the JSON in a POST request to Knora.
            val knoraPostRequest = Post(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc = getResponseJsonLD(knoraPostRequest)
            stillImageFileValueIri.set(responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri))

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the new file value from the resource.
            val savedValue: JsonLDObject = getValueFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri,
                expectedValueIri = stillImageFileValueIri.get
            )

            val savedImage = savedValueToSavedImage(savedValue)
            savedImage.internalFilename should ===(internalFilename)
            savedImage.width should ===(marblesWidth)
            savedImage.height should ===(marblesHeight)

            // Request the permanently stored image from Sipi.
            val sipiGetImageRequest = Get(savedImage.iiifUrl)
            checkResponseOK(sipiGetImageRequest)
        }

        "change a still image file" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToTrp88, mimeType = MediaTypes.`image/tiff`))
            )

            val internalFilename = sipiUploadResponse.uploadedFiles.head.internalFilename
            val resourceIri: IRI = aThingPictureIri

            // JSON describing the new image to Knora.
            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@id" : "${stillImageFileValueIri.get}",
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            // Send the JSON in a PUT request to Knora.
            val knoraPostRequest = Put(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc = getResponseJsonLD(knoraPostRequest)
            stillImageFileValueIri.set(responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri))

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the new file value from the resource.
            val savedValue: JsonLDObject = getValueFromResource(
                resource = resource,
                propertyIriInResult = OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri,
                expectedValueIri = stillImageFileValueIri.get
            )

            val savedImage = savedValueToSavedImage(savedValue)
            savedImage.internalFilename should ===(internalFilename)
            savedImage.width should ===(trp88Width)
            savedImage.height should ===(trp88Height)

            // Request the permanently stored image from Sipi.
            val sipiGetImageRequest = Get(savedImage.iiifUrl)
            checkResponseOK(sipiGetImageRequest)
        }

        "create a resource with two file values" in {
            // Upload the images to Sipi.

            val inputFiles = Seq(
                InputFile(fileToUpload = FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`), width = marblesWidth, height = marblesHeight),
                InputFile(fileToUpload = FileToUpload(path = pathToTrp88, mimeType = MediaTypes.`image/tiff`), width = trp88Width, height = trp88Height)
            )

            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = inputFiles.map(_.fileToUpload)
            )

            assert(sipiUploadResponse.uploadedFiles.size == inputFiles.size)

            // Ask Knora to create the resource.

            val jsonLdEntity =
                s"""{
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : [ {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "${sipiUploadResponse.uploadedFiles.head.internalFilename}"
                   |  }, {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "${sipiUploadResponse.uploadedFiles(1).internalFilename}"
                   |  } ],
                   |  "knora-api:attachedToProject" : {
                   |    "@id" : "http://rdfh.ch/projects/0001"
                   |  },
                   |  "rdfs:label" : "test thing",
                   |  "@context" : {
                   |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                   |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
            val responseJsonDoc: JsonLDDocument = getResponseJsonLD(request)
            val resourceIri: IRI = responseJsonDoc.body.requireStringWithValidation(JsonLDConstants.ID, stringFormatter.validateAndEscapeIri)
            assert(resourceIri.toSmartIri.isKnoraDataIri)

            // Get the resource from Knora.
            val knoraGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
            val resource = getResponseJsonLD(knoraGetRequest)

            // Get the image file values from the resource.

            val fileValues: JsonLDArray = getValuesFromResource(resource, OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri)

            val savedImages: Seq[SavedImage] = fileValues.value.map {
                case savedValue: JsonLDObject => savedValueToSavedImage(savedValue)
                case other => throw AssertionException(s"Expected JsonLDObject, got $other")
            }

            // Check that two file values were returned, that they're in the correct order, that they contain the
            // correct data, and that the images can be read from Sipi.

            assert(savedImages.size == inputFiles.size)
            val dataToCompare: Seq[(InputFile, SipiUploadResponseEntry, SavedImage)] = (inputFiles, sipiUploadResponse.uploadedFiles, savedImages).zipped.toSeq

            for ((inputFile: InputFile, uploadResponseEntry: SipiUploadResponseEntry, savedImage: SavedImage) <- dataToCompare) {
                savedImage.internalFilename should ===(uploadResponseEntry.internalFilename)
                savedImage.width should ===(inputFile.width)
                savedImage.height should ===(inputFile.height)
                val sipiGetImageRequest = Get(savedImage.iiifUrl)
                checkResponseOK(sipiGetImageRequest)
            }
        }

        "delete the temporary file if Knora rejects the request to create a file value" in {
            // Upload the image to Sipi.
            val sipiUploadResponse: SipiUploadResponse = uploadToSipi(
                loginToken = loginToken,
                filesToUpload = Seq(FileToUpload(path = pathToMarbles, mimeType = MediaTypes.`image/tiff`))
            )

            val internalFilename = sipiUploadResponse.uploadedFiles.head.internalFilename
            val temporaryBaseIIIFUrl = sipiUploadResponse.uploadedFiles.head.temporaryBaseIIIFUrl

            val resourceIri: IRI = aThingPictureIri

            // JSON describing the new image to Knora.
            val jsonLdEntity =
                s"""{
                   |  "@id" : "$resourceIri",
                   |  "@type" : "anything:ThingPicture",
                   |  "knora-api:hasStillImageFileValue" : {
                   |    "@type" : "knora-api:StillImageFileValue",
                   |    "knora-api:fileValueHasFilename" : "$internalFilename"
                   |  },
                   |  "@context" : {
                   |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                   |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                   |  }
                   |}""".stripMargin

            // Send the JSON in a POST request to Knora.
            val knoraPostRequest = Post(baseApiUrl + "/v2/values", HttpEntity(ContentTypes.`application/json`, jsonLdEntity)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password))
            val knoraPostResponse = singleAwaitingRequest(knoraPostRequest)
            assert(knoraPostResponse.status == StatusCodes.Forbidden)

            // Request the temporary image from Sipi.
            val sipiGetTmpFileRequest = Get(temporaryBaseIIIFUrl)
            val sipiResponse = singleAwaitingRequest(sipiGetTmpFileRequest)
            assert(sipiResponse.status == StatusCodes.NotFound)
        }
    }
}
