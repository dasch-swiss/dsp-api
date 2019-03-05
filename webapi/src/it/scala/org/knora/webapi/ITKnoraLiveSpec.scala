/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import java.io.File

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.util.{StartupUtils, StringFormatter}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.json.{JsObject, _}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext}
import scala.languageFeature.postfixOps

object ITKnoraLiveSpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class ITKnoraLiveSpec(_system: ActorSystem) extends Core with KnoraService with StartupUtils with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding with TriplestoreJsonProtocol  {

    implicit lazy val settings: SettingsImpl = Settings(system)

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(ITKnoraLiveSpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("IntegrationTests", config.withFallback(ITKnoraLiveSpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, ITKnoraLiveSpec.defaultConfig))

    def this() = this(ActorSystem("IntegrationTests", ITKnoraLiveSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    override implicit lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass.getName)

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl
    protected val baseSipiUrl: String = settings.internalSipiBaseUrl

    implicit protected val postfix: postfixOps = scala.language.postfixOps

    lazy val rdfDataObjects = List.empty[RdfDataObject]

    override def beforeAll: Unit = {

        // waits until the application state actor is ready
        applicationStateActorReady()

        // set allow reload over http
        applicationStateActor ! SetAllowReloadOverHTTPState(true)

        // start knora without loading ontologies
        startService(skipLoadingOfOntologies = true)

        // waits until knora is up and running
        applicationStateRunning()

        // check knora
        checkIfKnoraIsRunning()

        // check sipi
        checkIfSipiIsRunning()

        // loadTestData
        loadTestData(rdfDataObjects)
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        stopService()
    }

    protected def getResponseString(request: HttpRequest): String = {
        val response: HttpResponse = singleAwaitingRequest(request)
        val responseBodyStr: String = Await.result(response.entity.toStrict(10.seconds).map(_.data.decodeString("UTF-8")), 10.seconds)
        assert(response.status === StatusCodes.OK, s",\n REQUEST: $request,\n RESPONSE: $responseBodyStr")
        responseBodyStr
    }

    protected def checkResponseOK(request: HttpRequest): Unit = {
        getResponseString(request)
    }

    protected def getResponseJson(request: HttpRequest): JsObject = {
        getResponseString(request).parseJson.asJsObject
    }

    protected def checkIfKnoraIsRunning(): Unit = {
        val request = Get(baseApiUrl + "/health")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Knora is probably not running: ${response.status}")
        if (response.status.isSuccess()) log.info("Knora is running.")
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 8.minutes)
    }

    protected def checkIfSipiIsRunning(): Unit = {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
        if (response.status.isSuccess()) log.info("Sipi is running.")
        response.entity.discardBytes()
    }

    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 5999.milliseconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument = {
        val responseBodyStr = getResponseString(request)
        JsonLDUtil.parseJsonLD(responseBodyStr)
    }

    /**
      * Represents a file to be uploaded to Sipi.
      *
      * @param path     the path of the file.
      * @param mimeType the MIME type of the file.
      */
    protected case class FileToUpload(path: String, mimeType: ContentType)

    /**
      * Represents an image file to be uploaded to Sipi.
      *
      * @param fileToUpload the file to be uploaded.
      * @param width        the image's width in pixels.
      * @param height       the image's height in pixels.
      */
    protected case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

    /**
      * Represents the information that Sipi returns about each file that has been uploaded.
      *
      * @param originalFilename     the original filename that was submitted to Sipi.
      * @param internalFilename     Sipi's internal filename for the stored temporary file.
      * @param temporaryBaseIIIFUrl the base URL at which the temporary file can be accessed.
      */
    protected case class SipiUploadResponseEntry(originalFilename: String, internalFilename: String, temporaryBaseIIIFUrl: String)

    /**
      * Represents Sipi's response to a file upload request.
      *
      * @param uploadedFiles the information about each file that was uploaded.
      */
    protected case class SipiUploadResponse(uploadedFiles: Seq[SipiUploadResponseEntry])

    object SipiUploadResponseV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
        implicit val sipiUploadResponseEntryFormat: RootJsonFormat[SipiUploadResponseEntry] = jsonFormat3(SipiUploadResponseEntry)
        implicit val sipiUploadResponseFormat: RootJsonFormat[SipiUploadResponse] = jsonFormat1(SipiUploadResponse)
    }

    import SipiUploadResponseV2JsonProtocol._

    /**
      * Represents the information that Knora returns about an image file value that was created.
      *
      * @param internalFilename the image's internal filename.
      * @param iiifUrl          the image's IIIF URL.
      * @param width            the image's width in pixels.
      * @param height           the image's height in pixels.
      */
    protected case class SavedImage(internalFilename: String, iiifUrl: String, width: Int, height: Int)

    /**
      * Uploads a file to Sipi and returns the information in Sipi's response.
      *
      * @param loginToken    the login token to be included in the request to Sipi.
      * @param filesToUpload the files to be uploaded.
      * @return a [[SipiUploadResponse]] representing Sipi's response.
      */
    protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse = {
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

}
