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
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.util.StringFormatter
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite, WordSpecLike}
import spray.json.{JsObject, _}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.languageFeature.postfixOps


object ITKnoraFakeSpec {
    val defaultConfig: Config = ConfigFactory.load()
}

/**
  * This class can be used in End-to-End testing. It starts a Fake Knora server and
  * provides access to settings and logging.
  */
class ITKnoraFakeSpec(_system: ActorSystem) extends Core with KnoraFakeService with Suite with WordSpecLike with Matchers with BeforeAndAfterAll with RequestBuilding {

    /* needed by the core trait */
    implicit lazy val settings: SettingsImpl = Settings(system)

    StringFormatter.initForTest()

    def this(name: String, config: Config) = this(ActorSystem(name, config.withFallback(ITKnoraFakeSpec.defaultConfig)))

    def this(config: Config) = this(ActorSystem("IntegrationTests", config.withFallback(ITKnoraFakeSpec.defaultConfig)))

    def this(name: String) = this(ActorSystem(name, ITKnoraFakeSpec.defaultConfig))

    def this() = this(ActorSystem("IntegrationTests", ITKnoraFakeSpec.defaultConfig))

    /* needed by the core trait */
    implicit lazy val system: ActorSystem = _system

    /* needed by the core trait */
    implicit lazy val log: LoggingAdapter = akka.event.Logging(system, "ITSpec")

    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl
    protected val baseSipiUrl: String = settings.internalSipiBaseUrl

    override def beforeAll: Unit = {
        /* Set the startup flags and start the Knora Server */
        log.debug(s"Starting Knora Service")
        startService()
    }

    override def afterAll: Unit = {
        /* Stop the server when everything else has finished */
        log.debug(s"Stopping Knora Service")
        stopService()
    }

    protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15.seconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        Await.result(responseFuture, duration)
    }

    protected def getResponseString(request: HttpRequest): String = {
        val response = singleAwaitingRequest(request)
        val responseBodyStr = Await.result(Unmarshal(response.entity).to[String], 6.seconds)
        assert(response.status === StatusCodes.OK, s",\n REQUEST: $request,\n RESPONSE: $responseBodyStr")
        responseBodyStr
    }

    protected def checkResponseOK(request: HttpRequest): Unit = {
        getResponseString(request)
    }

    protected def getResponseJson(request: HttpRequest): JsObject = {
        getResponseString(request).parseJson.asJsObject
    }

    /**
      * Creates the Knora API server's temporary upload directory if it doesn't exist.
      */
    def createTmpFileDir(): Unit = {
        if (!Files.exists(Paths.get(settings.tmpDataDir))) {
            try {
                val tmpDir = new File(settings.tmpDataDir)
                tmpDir.mkdir()
            } catch {
                case e: Throwable => throw FileWriteException(s"Tmp data directory ${settings.tmpDataDir} could not be created: ${e.getMessage}")
            }
        }
    }

}
