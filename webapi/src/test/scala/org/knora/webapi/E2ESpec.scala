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

import java.io.{ByteArrayInputStream, File, StringReader}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor, LiveManagers}
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.util.{FileUtil, StartupUtils, StringFormatter}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import resource.managed
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.languageFeature.postfixOps

object E2ESpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora-API server
 * and provides access to settings and logging.
 */
class E2ESpec(_system: ActorSystem) extends Core with StartupUtils with TriplestoreJsonProtocol with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding with LazyLogging {

    /* constructors */
    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(E2ESpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("E2ETest", config.withFallback(E2ESpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, E2ESpec.defaultConfig))

    def this() = this(ActorSystem("E2ETest", E2ESpec.defaultConfig))

    /* needed by the core trait */

    implicit lazy val system: ActorSystem = _system
    implicit lazy val settings: SettingsImpl = Settings(system)
    implicit val materializer: Materializer = Materializer.matFromSystem(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    // can be overridden in individual spec
    lazy val rdfDataObjects = Seq.empty[RdfDataObject]

    /* Needs to be initialized before any responders */
    StringFormatter.initForTest()

    val log = akka.event.Logging(system, this.getClass)

    lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

    override def beforeAll: Unit = {

        // set allow reload over http
        appActor ! SetAllowReloadOverHTTPState(true)

        // start the knora service without loading of the ontologies
        appActor ! AppStart(skipLoadingOfOntologies = true, requiresIIIFService = false)

        // waits until knora is up and running
        applicationStateRunning()

        // loadTestData
        loadTestData(rdfDataObjects)
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        appActor ! AppStop()
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        logger.info("Loading test data started ...")
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 479999.milliseconds)
        logger.info("Loading test data done.")
    }

    // duration is intentionally like this, so that it could be found with search if seen in a stack trace
    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15999.milliseconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
        val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
        val responseBodyStr = Await.result(responseBodyFuture, 5.seconds)
        JsonLDUtil.parseJsonLD(responseBodyStr)
    }

    protected def responseToString(httpResponse: HttpResponse): String = {
        val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
        Await.result(responseBodyFuture, 5.seconds)
    }

    protected def doGetRequest(urlPath: String): String = {
        val request = Get(s"$baseApiUrl$urlPath")
        val response: HttpResponse = singleAwaitingRequest(request)
        responseToString(response)
    }

    protected def parseTrig(trigStr: String): Model = {
        Rio.parse(new StringReader(trigStr), "", RDFFormat.TRIG)
    }

    protected def parseTurtle(turtleStr: String): Model = {
        Rio.parse(new StringReader(turtleStr), "", RDFFormat.TURTLE)
    }

    protected def parseRdfXml(rdfXmlStr: String): Model = {
        Rio.parse(new StringReader(rdfXmlStr), "", RDFFormat.RDFXML)
    }

    protected def getResponseEntityBytes(httpResponse: HttpResponse): Array[Byte] = {
        val responseBodyFuture: Future[Array[Byte]] = httpResponse.entity.toStrict(5.seconds).map(_.data.toArray)
        Await.result(responseBodyFuture, 5.seconds)
    }

    protected def getZipContents(responseBytes: Array[Byte]): Set[String] = {
        val zippedFilenames = collection.mutable.Set.empty[String]

        for (zipInputStream <- managed(new ZipInputStream(new ByteArrayInputStream(responseBytes)))) {
            var zipEntry: ZipEntry = null

            while ( {
                zipEntry = zipInputStream.getNextEntry
                zipEntry != null
            }) {
                zippedFilenames.add(zipEntry.getName)
            }
        }

        zippedFilenames.toSet
    }

    def unzip(zipFilePath: Path, outputPath: Path): Unit = {
        val zipFile = new ZipFile(zipFilePath.toFile)

        for (entry <- zipFile.entries.asScala) {
            val entryPath = outputPath.resolve(entry.getName)

            if (entry.isDirectory) {
                Files.createDirectories(entryPath)
            } else {
                Files.createDirectories(entryPath.getParent)
                Files.copy(zipFile.getInputStream(entry), entryPath)
            }
        }
    }

    /**
     * Reads or writes a test data file.
     *
     * @param responseAsString the API response received from Knora.
     * @param file             the file in which the expected API response is stored.
     * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
     * @return the expected response.
     */
    protected def readOrWriteTextFile(responseAsString: String, file: File, writeFile: Boolean = false): String = {
        if (writeFile) {
            FileUtil.writeTextFile(file, responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL"))
            responseAsString
        } else {
            FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
        }
    }
}
