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
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.StringFormatter
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.languageFeature.postfixOps

object E2ESpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class E2ESpec(_system: ActorSystem) extends Core with KnoraService with TriplestoreJsonProtocol with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding {

    implicit lazy val settings: SettingsImpl = Settings(system)

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(E2ESpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("E2ETest", config.withFallback(E2ESpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, E2ESpec.defaultConfig))

    def this() = this(ActorSystem("E2ETest", E2ESpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass.getName)

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

    implicit protected val postfix: postfixOps = scala.language.postfixOps

    lazy val rdfDataObjects = List.empty[RdfDataObject]

    override def beforeAll: Unit = {

        // waits until the application state actor is ready
        applicationStateActorReady()

        // set allow reload over http
        applicationStateActor ! SetAllowReloadOverHTTPState(true)

        // start the knora service without loading of the ontologies
        startService(false)

        // waits until knora is up and running
        applicationStateRunning()

        // check if knora is running
        checkIfKnoraIsRunning()

        // loadTestData
        loadTestData(rdfDataObjects)
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        stopService()
    }

    protected def checkIfKnoraIsRunning(): Unit = {
        val request = Get(baseApiUrl + "/health")
        val response = singleAwaitingRequest(request)
        assert(response.status == StatusCodes.OK, s"Knora is probably not running: ${response.status}")
        if (response.status.isSuccess()) log.info("Knora is running.")
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 5 minutes)
    }

    // duration is intentionally like this, so that it could be found with search if seen in a stack trace
    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 2999 milliseconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

}
