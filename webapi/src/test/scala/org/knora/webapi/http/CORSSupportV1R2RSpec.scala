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

package org.knora.webapi.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Methods`, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsRejection
import org.knora.webapi.R2RSpec
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.routing.v1.ResourcesRouteV1

import scala.concurrent.duration._

/**
  * End-to-end test specification for testing [[CORSSupport]].
  */
class CORSSupportV1R2RSpec extends R2RSpec {

    /* get the path of the route we want to test */
    private val sealedResourcesRoute = Route.seal(ResourcesRouteV1.knoraApiPath(system, settings, log))

    /* set the timeout for the route test */
    implicit val timeout: Timeout = 5.seconds
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    val exampleOrigin = HttpOrigin("http://example.com")
    val corsSettings = CORSSupport.corsSettings

    "A Route with enabled CORS support " should {

        "accept valid pre-flight requests" in {

            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(GET) ~> {
                CORS(sealedResourcesRoute, settings, log)
            } ~> check {
                responseAs[String] shouldBe empty
                status shouldBe StatusCodes.OK
                response.headers should contain theSameElementsAs Seq(
                    `Access-Control-Allow-Origin`(exampleOrigin),
                    `Access-Control-Allow-Methods`(corsSettings.allowedMethods),
                    //`Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Authorization"),
                    `Access-Control-Max-Age`(1800),
                    `Access-Control-Allow-Credentials`(true)
                )
            }
        }

        "reject pre-flight requests with invalid method" in {

            val invalidMethod = PATCH
            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(invalidMethod) ~> {
                CORS(sealedResourcesRoute, settings, log)
            } ~> check {
                status shouldBe StatusCodes.BadRequest
                entityAs[String] should equal("CORS: invalid method 'PATCH'")
            }
        }

    }
}
