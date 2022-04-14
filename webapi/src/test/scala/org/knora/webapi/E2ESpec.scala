/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
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
import org.knora.webapi.exceptions.FileWriteException

object E2ESpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora-API server
 * and provides access to settings and logging.
 */
class E2ESpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
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
    this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))))

  def this(config: Config) =
    this(ActorSystem("E2ETest", TestContainerFuseki.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))))

  def this(name: String) = this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(E2ESpec.defaultConfig)))

  def this() = this(ActorSystem("E2ETest", TestContainerFuseki.PortConfig.withFallback(E2ESpec.defaultConfig)))

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  /* Needs to be initialized before any responders */
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  // using here the TestManagersWithAllTestContainers trait will also initiate all TestContainers
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor with TestManagersWithAllTestContainers),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set.empty,
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  override def beforeAll(): Unit = {

    // create temp data dir if not present
    createTmpFileDir()

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // start the knora service, loading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)

  }

  override def afterAll(): Unit =
    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)

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
   *
   * @param responseAsString the API response received from Knora.
   * @param file             the file in which the expected API response is stored.
   * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
   * @return the expected response.
   */
  protected def readOrWriteTextFile(responseAsString: String, file: Path, writeFile: Boolean = false): String =
    if (writeFile) {
      val testOutputDir = Paths.get("..", "test_data", "ontologyR2RV2")
      val newOutputFile = testOutputDir.resolve(file)
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(
        newOutputFile,
        responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL")
      )
      responseAsString
    } else {
      FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
    }

  private def createTmpFileDir(): Unit = {
    // check if tmp datadir exists and create it if not
    val tmpFileDir = Path.of(settings.tmpDataDir)

    if (!Files.exists(tmpFileDir)) {
      try {
        Files.createDirectories(tmpFileDir)
      } catch {
        case e: Throwable =>
          throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
      }
    }
  }
}
