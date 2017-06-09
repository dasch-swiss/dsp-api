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
import com.typesafe.config.ConfigFactory
import org.knora.salsah.SettingsImpl
import org.scalatest._
import spray.client.pipelining._
import spray.http.MediaTypes._
import spray.http.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * An abstract base class for Selenium tests of the SALSAH user interface.
  */
abstract class SalsahSpec extends WordSpecLike with ShouldMatchers {
    protected val settings = new SettingsImpl(ConfigFactory.load())

    /**
      * Loads test data and populates the ontology cache.
      *
      * @param rdfDataObjectsJsonList a JSON array specifying data files to be loaded into the triplestore.
      */
    protected def loadTestData(rdfDataObjectsJsonList: String)(implicit system: ActorSystem, executionContext: ExecutionContext): Unit = {
        // Load the test data into the triplestore.
        val loadDataRequest: HttpRequest = Post(s"${settings.webapiUrl}/v1/store/ResetTriplestoreContent", HttpEntity(`application/json`, rdfDataObjectsJsonList))
        makeHttpRequest(loadDataRequest, Duration("180 seconds"))

        // Populate the ontology cache.
        val loadOntologiesRequest: HttpRequest = Get(s"${settings.webapiUrl}/v1/vocabularies/reload")
        makeHttpRequest(loadOntologiesRequest, Duration("10 seconds"))
    }

    /**
      * Makes an HTTP request and checks that the response is HTTP 200 (OK).
      *
      * @param request the request to send.
      */
    private def makeHttpRequest(request: HttpRequest, timeout: Duration)(implicit system: ActorSystem, executionContext: ExecutionContext): Unit = {
        // define a pipeline function that gets turned into a generic [[HTTP Response]] (containing JSON)
        val pipeline: HttpRequest => Future[HttpResponse] = (
            addHeader("Accept", "application/json")
                ~> sendReceive
                ~> unmarshal[HttpResponse]
            )

        val loadRequestFuture: Future[HttpResponse] = for {
            requestFuture <- Future(request)
            pipelineResult <- pipeline(requestFuture)
        } yield pipelineResult

        val requestResponse = Await.result(loadRequestFuture, timeout)

        assert(requestResponse.status == StatusCodes.OK)
    }
}
