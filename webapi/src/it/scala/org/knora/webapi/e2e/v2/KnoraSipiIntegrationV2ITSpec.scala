package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

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
import spray.json.JsString

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
    private val anythingUserEmail = "anything.user01@example.org"
    private val incunabulaUserEmail = "test.user2@test.ch"
    private val password = "test"
    private val stillImageFileValueIri = new MutableTestIri
    private val aThingPictureIri = "http://rdfh.ch/0001/a-thing-picture"

    private def getValuesFromResource(resource: JsonLDDocument,
                                      propertyIriInResult: SmartIri): JsonLDArray = {
        resource.requireArray(propertyIriInResult.toString)
    }

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

        "store a still image file" in {
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
            val sipiRequest = Post(s"$baseSipiUrl/upload?token=$loginToken", sipiFormData)
            val sipiResponseJson = getResponseJson(sipiRequest)

            val (internalFilename: String, tempBaseUrl: String) = sipiResponseJson.fields.head match {
                case (jsonKey, JsString(jsonValue)) => (jsonKey, jsonValue)
                case _ => throw AssertionException(s"Invalid response from Sipi: ${sipiResponseJson.compactPrint}")
            }

            // Request the temporary image from Sipi.
            val sipiGetTmpFileRequest = Get(tempBaseUrl + "/full/full/0/default.jpg")
            checkResponseOK(sipiGetTmpFileRequest)

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

            savedValue.requireString(OntologyConstants.KnoraApiV2WithValueObjects.FileValueHasFilename) should ===(internalFilename)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimX) should ===(1419)
            savedValue.requireInt(OntologyConstants.KnoraApiV2WithValueObjects.StillImageFileValueHasDimY) should ===(1001)

            val iiifUrl = savedValue.requireDatatypeValueInObject(
                key = OntologyConstants.KnoraApiV2WithValueObjects.FileValueAsUrl,
                expectedDatatype = OntologyConstants.Xsd.Uri.toSmartIri,
                validationFun = stringFormatter.toSparqlEncodedString
            )

            // Request the permanently stored image from Sipi.
            val sipiGetImageRequest = Get(iiifUrl)
            checkResponseOK(sipiGetImageRequest)
        }

        "delete the temporary file if Knora rejects the request to create a file value" in {
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
            val sipiRequest = Post(s"$baseSipiUrl/upload?token=$loginToken", sipiFormData)
            val sipiResponseJson = getResponseJson(sipiRequest)

            val (internalFilename: String, tempBaseUrl: String) = sipiResponseJson.fields.head match {
                case (jsonKey, JsString(jsonValue)) => (jsonKey, jsonValue)
                case _ => throw AssertionException(s"Invalid response from Sipi: ${sipiResponseJson.compactPrint}")
            }

            // Request the temporary image from Sipi.
            val sipiGetTmpFileRequest = Get(tempBaseUrl + "/full/full/0/default.jpg")
            checkResponseOK(sipiGetTmpFileRequest)

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
            val sipiResponse = singleAwaitingRequest(sipiGetTmpFileRequest)
            assert(sipiResponse.status == StatusCodes.NotFound)
        }
    }
}
