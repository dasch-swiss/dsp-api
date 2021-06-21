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

import app.{ApplicationActor, LiveManagers}
import core.Core
import feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import messages.StringFormatter
import messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import messages.util.rdf._
import settings._
import util.{FileUtil, StartupUtils}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}
import spray.json._

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.languageFeature.postfixOps
import scala.util.{Failure, Success, Try}

object E2ESpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora-API server
 * and provides access to settings and logging.
 */
class E2ESpec(_system: ActorSystem)
    extends Core
    with StartupUtils
    with TriplestoreJsonProtocol
    with Suite
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with RequestBuilding
    with LazyLogging {

  /* constructors */
  def this(name: String, config: Config) =
    this(ActorSystem(name, TestContainersAll.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))))

  def this(config: Config) =
    this(ActorSystem("E2ETest", TestContainersAll.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))))

  def this(name: String) = this(ActorSystem(name, TestContainersAll.PortConfig.withFallback(E2ESpec.defaultConfig)))

  def this() = this(ActorSystem("E2ETest", TestContainersAll.PortConfig.withFallback(E2ESpec.defaultConfig)))

  /* needed by the core trait */

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

  protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set.empty,
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // start the knora service, loading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)

  }

  override def afterAll(): Unit =
    /* Stop the server when everything else has finished */
    appActor ! AppStop()

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    val request = Post(
      baseApiUrl + "/admin/store/ResetTriplestoreContent",
      HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
    )
    val response = Http().singleRequest(request)

    Try(Await.result(response, 479999.milliseconds)) match {
      case Success(res) => logger.info("... loading test data done.")
      case Failure(e)   => logger.error(s"Loading test data failed: ${e.getMessage}")
    }
  }

  // duration is intentionally like this, so that it could be found with search if seen in a stack trace
  protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15999.milliseconds): HttpResponse = {
    val responseFuture = Http().singleRequest(request)
    Await.result(responseFuture, duration)
  }

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(10.seconds).map(_.data.decodeString("UTF-8"))
    val responseBodyStr                    = Await.result(responseBodyFuture, 10.seconds)
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def responseToString(httpResponse: HttpResponse): String = {
    val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(10.seconds).map(_.data.decodeString("UTF-8"))
    Await.result(responseBodyFuture, 10.seconds)
  }

  protected def doGetRequest(urlPath: String): String = {
    val request                = Get(s"$baseApiUrl$urlPath")
    val response: HttpResponse = singleAwaitingRequest(request)
    responseToString(response)
  }

  protected def parseTrig(trigStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)
    rdfFormatUtil.parseToRdfModel(rdfStr = trigStr, rdfFormat = TriG)
  }

  protected def parseTurtle(turtleStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)
    rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
  }

  protected def parseRdfXml(rdfXmlStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)
    rdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = RdfXml)
  }

  protected def getResponseEntityBytes(httpResponse: HttpResponse): Array[Byte] = {
    val responseBodyFuture: Future[Array[Byte]] = httpResponse.entity.toStrict(10.seconds).map(_.data.toArray)
    Await.result(responseBodyFuture, 10.seconds)
  }

  /**
   * Reads or writes a test data file.
   * The written test data files can be found under:
   * ./bazel-out/darwin-fastbuild/testlogs/<package-name>/<target-name>/test.outputs/outputs.zip
   *
   * @param responseAsString the API response received from Knora.
   * @param file             the file in which the expected API response is stored.
   * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
   * @return the expected response.
   */
  protected def readOrWriteTextFile(responseAsString: String, file: Path, writeFile: Boolean = false): String =
    if (writeFile) {
      // Per default only read access is allowed in the bazel sandbox.
      // This workaround allows to save test output.
      val testOutputDir: Path = Paths.get(sys.env("TEST_UNDECLARED_OUTPUTS_DIR"))
      val newOutputFile       = testOutputDir.resolve(file)
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(
        newOutputFile,
        responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL")
      )
      responseAsString
    } else {
      FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
    }
}
