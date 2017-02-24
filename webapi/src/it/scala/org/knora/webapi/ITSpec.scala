/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.languageFeature.postfixOps

object ITSpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts the Knora server and
  * provides access to settings and logging.
  */
class ITSpec(_system: ActorSystem) extends Core with KnoraService with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding {

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(ITSpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("IntegrationTests", config.withFallback(ITSpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, ITSpec.defaultConfig))

    def this() = this(ActorSystem("IntegrationTests", ITSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    if (!settings.knoraApiUseHttp) throw HttpConfigurationException("Integration tests currently require HTTP")

    protected val baseApiUrl: String = settings.knoraApiHttpBaseUrl
    protected val baseSipiUrl: String = s"${settings.sipiBaseUrl}:${settings.sipiPort}"

    implicit protected val postfix: postfixOps = scala.language.postfixOps

    implicit protected val ec: ExecutionContextExecutor = system.dispatcher
    implicit protected val materializer = ActorMaterializer()

    def singleAwaitingRequest(request: HttpRequest, duration: Duration = 5.seconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    override def beforeAll: Unit = {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true
        checkActorSystem()
        startService()
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        stopService()
    }

}
