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
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.gatling.core.scenario.Simulation
import org.knora.webapi.app.{APPLICATION_MANAGER_ACTOR_NAME, ApplicationActor, LiveManagers}
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.StringFormatter
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

object E2ESimSpec {

    val config: Config = ConfigFactory.load()

    val defaultConfig: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "INFO"
          akka.stdout-loglevel = "INFO"
        """.stripMargin
    ).withFallback(config)
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
abstract class E2ESimSpec(_system: ActorSystem) extends Simulation with Core with TriplestoreJsonProtocol with RequestBuilding with LazyLogging {

    /* constructors */
    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(E2ESimSpec.defaultConfig)))
    def this(config: Config) = this(ActorSystem("PerfSpec", config.withFallback(E2ESimSpec.defaultConfig)))
    def this(name: String) = this(ActorSystem(name, E2ESimSpec.defaultConfig))
    def this() = this(ActorSystem("PerfSpec", E2ESimSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system
    implicit lazy val settings: SettingsImpl = Settings(system)
    implicit val materializer: Materializer = Materializer.matFromSystem(system)
    implicit val executionContext: ExecutionContext = system.dispatchers.defaultGlobalDispatcher

    // can be overridden in individual spec
    lazy val rdfDataObjects = Seq.empty[RdfDataObject]

    /* Needs to be initialized before any responders */
    StringFormatter.initForTest()

    val log = akka.event.Logging(system, this.getClass)

    lazy val appActor: ActorRef = system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

    before {
        /* Set the startup flags and start the Knora Server */
        logger.info(s"executing before setup started")

        appActor ! SetAllowReloadOverHTTPState(true)

        appActor ! AppStart(skipLoadingOfOntologies = true, requiresIIIFService = false)

        loadTestData(rdfDataObjects)

        logger.info(s"executing before setup finished")
    }

    after {
        /* Stop the server when everything else has finished */
        logger.info(s"executing after setup")
        appActor ! AppStop()
    }

    protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 5 minutes)
    }

    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 3.seconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }
}
