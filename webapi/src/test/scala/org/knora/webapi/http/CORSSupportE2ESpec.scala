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

package org.knora.webapi.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Methods`, _}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object CORSSupportE2ESpec {
    val config = ConfigFactory.parseString(
        """
            akka.loglevel = "DEBUG"
            akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-end test specification for testing [[CORSSupport]].
  */
class CORSSupportE2ESpec extends E2ESpec(CORSSupportE2ESpec.config) {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(settings.defaultTimeout)

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )
    
    val exampleOrigin = HttpOrigin("http://example.com")

    "A Route with enabled CORS support" should {

        "accept valid pre-flight requests" in {
            val request = Options(baseApiUrl + s"/admin/projects") ~> Origin(exampleOrigin)
            val response: HttpResponse = singleAwaitingRequest(request)
            println(s"response: ${response.toString}")
            logger.debug(s"response: ${response.toString}")
            response.status shouldBe StatusCodes.OK
            response.entity.toString shouldBe empty
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin),
                `Access-Control-Allow-Methods`(List(GET, PUT, POST, DELETE, HEAD, OPTIONS)),
                // `Access-Control-Allow-Headers`(CORSSupport.exposedHeaders),
                `Access-Control-Max-Age`(1800),
                `Access-Control-Allow-Credentials`(true)
            )
        }

        "reject pre-flight requests with invalid method" in {
            val request = Patch(baseApiUrl + s"/admin/projects")
            val response: HttpResponse = singleAwaitingRequest(request)
            println(s"response: ${response.toString}")
            log.debug(s"response: ${response.toString}")
            responseToString(response) shouldEqual "HTTP method not allowed, supported methods: GET, PUT, POST, DELETE, HEAD, OPTIONS"
            response.status shouldBe StatusCodes.BadRequest
        }

        "send `Access-Control-Allow-Origin` header when the Knora resource is found " in {
            val request = Get(baseApiUrl + "/v1/resources/" + java.net.URLEncoder.encode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg", "utf-8")) ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)
            println(s"response: ${response.toString}")
            log.debug(s"response: ${response.toString}")
            response.status should equal(StatusCodes.OK)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

        "send `Access-Control-Allow-Origin` header when the Knora resource is NOT found " in {
            val request = Get(baseApiUrl + "/v1/resources/" + java.net.URLEncoder.encode("http://rdfh.ch/0803/nonexistent", "utf-8")) ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)
            println(s"response: ${response.toString}")
            log.debug(s"response: ${response.toString}")
            response.status should equal(StatusCodes.NotFound)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

        "send `Access-Control-Allow-Origin` header when the api endpoint route is NOT found " in {
            val request = Get(baseApiUrl + "/NotFound") ~> Origin(exampleOrigin)
            val response = singleAwaitingRequest(request)
            println(s"response: ${response.toString}")
            log.debug(s"response: ${response.toString}")
            response.status should equal(StatusCodes.NotFound)
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin)
            )
        }

    }
}
