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

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi.E2ESpec
import org.knora.webapi.http.CORSSupport
import org.knora.webapi.routing.v1.{ResourcesRouteV1, StoreRouteV1}

import scala.concurrent.duration._

/**
  * End-to-end test specification for testing [[StoreRouteV1]]. This specification uses the
  * Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class CORSSupportV1E2ESpec extends E2ESpec {

    /* set the timeout for the route test */
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(180).second)

    val exampleOrigin = HttpOrigin("http://example.com")
    val corsSettings = CORSSupport.corsSettings

    "A Route with enabled CORS support " should {

        "accept valid pre-flight requests" ignore {

            /*
            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(GET) ~> {
                CORS(resourcesRoute)
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
            */
        }

        "reject pre-flight requests with invalid method" ignore {

            /*
            val invalidMethod = PATCH
            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(invalidMethod) ~> {
                CORS(resourcesRoute)
            } ~> check {
                rejection shouldBe CorsRejection(None, Some(invalidMethod), None)
            }
            */
        }

    }
}
