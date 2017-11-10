/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Methods`, _}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.http.CORSSupport
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object CORSSupportV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-end test specification for testing [[CORSSupport]].
  */
class CORSSupportV1E2ESpec extends E2ESpec(CORSSupportV1E2ESpec.config) with TriplestoreJsonProtocol {

    /* set the timeout for the route test */
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(180).second)

    val exampleOrigin = HttpOrigin("http://example.com")
    val corsSettings = CORSSupport.corsSettings

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "A Route with enabled CORS support " should {

        "accept valid pre-flight requests" in {

            val request = Options(baseApiUrl + "/v1/authenticate") ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(GET)
            val response = singleAwaitingRequest(request)

            response.status should equal(StatusCodes.OK)

            val headersMinusDate = response.headers.filter(Date => false)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin),
                `Access-Control-Allow-Methods`(corsSettings.allowedMethods),
                //`Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Authorization"),
                `Access-Control-Max-Age`(1800),
                `Access-Control-Allow-Credentials`(true)
            )
        }

        "reject pre-flight requests with invalid method" in {
            val invalidMethod = PATCH
            val request = Options(baseApiUrl + "/v1/authenticate") ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(invalidMethod)
            val response = singleAwaitingRequest(request)

            val entity = Await.result(Unmarshal(response.entity).to[String], 1.seconds)

            response.status should equal(StatusCodes.BadRequest)
            entity should equal("CORS: invalid method 'PATCH'")
        }

        "send `Access-Control-Allow-Origin` header when the Knora resource is found " in {
            val request = Get(baseApiUrl + "/v1/resources/" + java.net.URLEncoder.encode("http://data.knora.org/0cb8286054d5", "utf-8")) ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)

            response.status should equal(StatusCodes.OK)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

        "send `Access-Control-Allow-Origin` header when the Knora resource is NOT found " in {
            val request = Get(baseApiUrl + "/v1/resources/" + java.net.URLEncoder.encode("http://data.knora.org/nonexistent", "utf-8")) ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)

            response.status should equal(StatusCodes.NotFound)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

        "send `Access-Control-Allow-Origin` header when the api endpoint route is NOT found " in {
            val request = Get(baseApiUrl + "/NotFound") ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)

            response.status should equal(StatusCodes.NotFound)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

    }
}
