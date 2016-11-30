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

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.{BadRequestException, SettingsImpl}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation

import scala.xml.NodeSeq


/**
  * A route used to convert XML to standoff.
  */
object StandoffRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "standoff" / Segments) { (iris: List[String]) =>
            post {
                entity(as[String]) { xml: String =>
                    requestContext =>

                        // iris should contain the resource IRI, the property IRI, and the project IRI
                        if (iris.size != 3) {
                            throw BadRequestException(s"Expected two segments after segment 'standoff/': resourceIri/propertyIri/projectIri")
                        }

                        val resourceIri = InputValidation.toIri(iris(0), () => throw BadRequestException(s"invalid IRI ${iris(0)}"))
                        val propertyIri = InputValidation.toIri(iris(1), () => throw BadRequestException(s"invalid IRI ${iris(1)}"))
                        val projectIri = InputValidation.toIri(iris(2), () => throw BadRequestException(s"invalid IRI ${iris(2)}"))


                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessage = CreateStandoffRequestV1(projectIri = projectIri, resourceIri = resourceIri, propertyIri = propertyIri, xml = xml, userProfile = userProfile, apiRequestID = UUID.randomUUID)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~ path("v1" / "standoff" / Segment) { iri: String =>
            get {
                requestContext => {

                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = StandoffGetRequestV1(valueIri = InputValidation.toIri(iri, () => throw BadRequestException("invalid Iri")), userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log)
                }
            }
        } ~path("v1" / "mapping") {
            post {
                entity(as[String]) { xml:String =>
                    requestContext =>

                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessage = CreateMappingRequestV1(xml = xml, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        }
    }
}