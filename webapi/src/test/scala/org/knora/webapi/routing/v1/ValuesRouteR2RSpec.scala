/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.routing.v1

/**
  * TODO: document this.
  */
/*
class ValuesRouteR2RSpec extends CoreSpec("ValuesRouteR2RSpec") with ScalatestRouteTest with HttpService with ImplicitSender {
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with TestProbeMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    "The values route" should {
        "serve path(\"v1\" / \"values\" / Segments)" ignore {
            val iri = "http://data.knora.org/e41ab5695c/values/d3398239089e04"

            Get("/v1/values/" + URLEncoder.encode(iri, "UTF-8")) ~> ValuesRouteV1.rapierPath(system, Settings(system), system.log) {
                expectMsg(ValueGetRequestV1(iri, _))
            }

        }
    }
}
*/