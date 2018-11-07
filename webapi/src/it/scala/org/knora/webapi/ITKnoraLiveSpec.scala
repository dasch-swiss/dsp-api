/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.json.{JsObject, _}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.languageFeature.postfixOps

object ITKnoraLiveSpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class ITKnoraLiveSpec(_system: ActorSystem) extends Core with KnoraService with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding with TriplestoreJsonProtocol  {

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
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass.getName)

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
        startService(false)

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
        val response = singleAwaitingRequest(request)

        assert(response.status === StatusCodes.OK, s",\n REQUEST: $request,\n RESPONSE: $response")

        //log.debug("REQUEST: {}", request)
        //log.debug("RESPONSE: {}", response.toString())

        val responseBodyStr = Await.result(Unmarshal(response.entity).to[String], 6.seconds)

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
        singleAwaitingRequest(request, 8 minutes)
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
}
