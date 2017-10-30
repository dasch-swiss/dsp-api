/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.salsah.browser

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.knora.salsah.SettingsImpl
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * An abstract base class for Selenium tests of the SALSAH user interface.
  */
abstract class SalsahSpec extends WordSpecLike with Matchers with RequestBuilding {

    implicit private val system = ActorSystem()

    protected val settings = new SettingsImpl(ConfigFactory.load())

    implicit private val timeout = Timeout(180.seconds)
    implicit private val dispatcher = system.dispatcher
    implicit protected val ec: ExecutionContextExecutor = dispatcher
    implicit protected val materializer = ActorMaterializer()

    /**
      * Loads test data and populates the ontology cache.
      *
      * @param rdfDataObjectsJsonList a JSON array specifying data files to be loaded into the triplestore.
      */
    protected def loadTestData(rdfDataObjectsJsonList: String): Unit = {

        val request = Post(settings.webapiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjectsJsonList))
        singleAwaitingRequest(request, 300.seconds)
    }

    /**
      * Makes a single HTTP request, waits for the result and chekcs that the response is HTTP 200 (OK)
      * @param request the request to send.
      * @param duration the max wait time.
      */
    def singleAwaitingRequest(request: HttpRequest, duration: Duration = 3.seconds): HttpResponse = {
        val responseFuture = Http().singleRequest(request)
        val response = Await.result(responseFuture, duration)

        assert(response.status == StatusCodes.OK)
        response
    }
}
