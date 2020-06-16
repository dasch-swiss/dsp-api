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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor, LiveManagers}
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.util.{StartupUtils, StringFormatter}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}
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
class ITKnoraLiveSpec(_system: ActorSystem) extends Core with StartupUtils with Suite with AnyWordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding with TriplestoreJsonProtocol with LazyLogging {

    /* constructors */
    def this(name: String, config: Config) = this(ActorSystem(name, TestContainers.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig))))
    def this(config: Config) = this(ActorSystem("IntegrationTests", TestContainers.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig))))
    def this(name: String) = this(ActorSystem(name, TestContainers.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))
    def this() = this(ActorSystem("IntegrationTests", TestContainers.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))

    /* needed by the core trait (represents the KnoraTestCore trait)*/
    implicit lazy val system: ActorSystem = _system
    implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)
    implicit val materializer: Materializer = Materializer.matFromSystem(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    // can be overridden in individual spec
    lazy val rdfDataObjects = Seq.empty[RdfDataObject]

    /* Needs to be initialized before any responders */
    StringFormatter.initForTest()

    val log = akka.event.Logging(system, this.getClass)

    lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl
    protected val baseSipiUrl: String = settings.internalSipiBaseUrl

    override def beforeAll: Unit = {

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

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        appActor ! AppStop()
    }

    protected def checkIfSipiIsRunning(): Unit = {
        // This requires that (1) fileserver.docroot is set in Sipi's config file and (2) it contains a file test.html.
        val request = Get(baseSipiUrl + "/server/test.html")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Sipi is probably not running: ${response.status}")
        if (response.status.isSuccess()) logger.info("Sipi is running.")
        response.entity.discardBytes()
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        logger.info("Loading test data started ...")
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 479999.milliseconds)
        logger.info("... loading test data done.")
    }

    protected def getResponseString(request: HttpRequest): String = {
        val response: HttpResponse = singleAwaitingRequest(request)
        val responseBodyStr: String = Await.result(response.entity.toStrict(10999.seconds).map(_.data.decodeString("UTF-8")), 10.seconds)

        if (response.status.isSuccess) {
            responseBodyStr
        } else {
            throw AssertionException(s"Got HTTP ${response.status.intValue}\n REQUEST: $request,\n RESPONSE: $responseBodyStr")
        }
    }

    protected def checkResponseOK(request: HttpRequest): Unit = {
        getResponseString(request)
    }

    protected def getResponseJson(request: HttpRequest): JsObject = {
        getResponseString(request).parseJson.asJsObject
    }

    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15999.milliseconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument = {
        val responseBodyStr = getResponseString(request)
        JsonLDUtil.parseJsonLD(responseBodyStr)
    }
}
