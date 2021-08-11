/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.app.{ApplicationActor, LiveManagers}
import org.knora.webapi.core.Core
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDUtil, RdfFeatureFactory}
import org.knora.webapi.settings._
import org.knora.webapi.util.StartupUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.languageFeature.postfixOps

object ITKnoraLiveSpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
class ITKnoraLiveSpec(_system: ActorSystem)
    extends Core
    with StartupUtils
    with Suite
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

  /* constructors */
  def this(name: String, config: Config) =
    this(
      ActorSystem(name, TestContainersAll.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig)))
    )
  def this(config: Config) =
    this(
      ActorSystem(
        "IntegrationTests",
        TestContainersAll.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig))
      )
    )
  def this(name: String) =
    this(ActorSystem(name, TestContainersAll.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))
  def this() =
    this(ActorSystem("IntegrationTests", TestContainersAll.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))

  /* needed by the core trait (represents the KnoraTestCore trait)*/
  implicit lazy val system: ActorSystem           = _system
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  /* Needs to be initialized before any responders */
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  lazy val appActor: ActorRef =
    system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

  protected val baseApiUrl: String          = settings.internalKnoraApiBaseUrl
  protected val baseInternalSipiUrl: String = settings.internalSipiBaseUrl
  protected val baseExternalSipiUrl: String = settings.externalSipiBaseUrl

  override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // check sipi
    checkIfSipiIsRunning()

    // loadTestData
    loadTestData(rdfDataObjects)
  }

  override def afterAll(): Unit =
    /* Stop the server when everything else has finished */
    appActor ! AppStop()

  protected def checkIfSipiIsRunning(): Unit = {
    // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
    val request  = Get(baseInternalSipiUrl + "/server/test.html")
    val response = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
    if (response.status.isSuccess()) logger.info("Sipi is running.")
    response.entity.discardBytes()
  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    val request = Post(
      baseApiUrl + "/admin/store/ResetTriplestoreContent",
      HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
    )
    singleAwaitingRequest(request, 479999.milliseconds)
    logger.info("... loading test data done.")
  }

  protected def getResponseStringOrThrow(request: HttpRequest): String = {
    val response: HttpResponse = singleAwaitingRequest(request)
    val responseBodyStr: String =
      Await.result(response.entity.toStrict(10999.seconds).map(_.data.decodeString("UTF-8")), 10.seconds)

    if (response.status.isSuccess) {
      responseBodyStr
    } else {
      throw AssertionException(
        s"Got HTTP ${response.status.intValue}\n REQUEST: $request,\n RESPONSE: $responseBodyStr"
      )
    }
  }

  protected def checkResponseOK(request: HttpRequest): Unit =
    getResponseStringOrThrow(request)

  protected def getResponseJson(request: HttpRequest): JsObject =
    getResponseStringOrThrow(request).parseJson.asJsObject

  protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15999.milliseconds): HttpResponse = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
    Await.result(responseFuture, duration)
  }

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument = {
    val responseBodyStr = getResponseStringOrThrow(request)
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
   * @param originalFilename the original filename that was submitted to Sipi.
   * @param internalFilename Sipi's internal filename for the stored temporary file.
   * @param temporaryUrl     the URL at which the temporary file can be accessed.
   * @param fileType         `image`, `text`, or `document`.
   */
  protected case class SipiUploadResponseEntry(
    originalFilename: String,
    internalFilename: String,
    temporaryUrl: String,
    fileType: String
  )

  /**
   * Represents Sipi's response to a file upload request.
   *
   * @param uploadedFiles the information about each file that was uploaded.
   */
  protected case class SipiUploadResponse(uploadedFiles: Seq[SipiUploadResponseEntry])

  object SipiUploadResponseJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val sipiUploadResponseEntryFormat: RootJsonFormat[SipiUploadResponseEntry] = jsonFormat4(
      SipiUploadResponseEntry
    )
    implicit val sipiUploadResponseFormat: RootJsonFormat[SipiUploadResponse] = jsonFormat1(SipiUploadResponse)
  }

  import SipiUploadResponseJsonProtocol._

  /**
   * Uploads a file to Sipi and returns the information in Sipi's response.
   *
   * @param loginToken    the login token to be included in the request to Sipi.
   * @param filesToUpload the files to be uploaded.
   * @return a [[SipiUploadResponse]] representing Sipi's response.
   */
  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse = {
    // Make a multipart/form-data request containing the files.

    val formDataParts: Seq[Multipart.FormData.BodyPart] = filesToUpload.map { fileToUpload =>
      val fileToSend: Path = Paths.get(fileToUpload.path)
      assert(Files.exists(fileToSend), s"File ${fileToUpload.path} does not exist")

      Multipart.FormData.BodyPart(
        "file",
        HttpEntity.fromPath(fileToUpload.mimeType, fileToSend),
        Map("filename" -> fileToSend.getFileName.toString)
      )
    }

    val sipiFormData = Multipart.FormData(formDataParts: _*)

    // Send Sipi the file in a POST request.
    val sipiRequest = Post(s"$baseInternalSipiUrl/upload?token=$loginToken", sipiFormData)

    val sipiUploadResponseJson: JsObject = getResponseJson(sipiRequest)

    val sipiUploadResponse: SipiUploadResponse = sipiUploadResponseJson.convertTo[SipiUploadResponse]

    // Request the temporary file from Sipi.
    for (responseEntry <- sipiUploadResponse.uploadedFiles) {
      val sipiGetTmpFileRequest: HttpRequest = if (responseEntry.fileType == "image") {
        Get(responseEntry.temporaryUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl) + "/full/max/0/default.jpg")
      } else {
        Get(responseEntry.temporaryUrl.replace("http://0.0.0.0:1024", baseInternalSipiUrl) + "/file")
      }

      checkResponseOK(sipiGetTmpFileRequest)
    }

    sipiUploadResponse
  }
}
