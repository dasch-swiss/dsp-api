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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.knora.webapi.messages.v1respondermessages.ontologymessages.{ResourceTypesForNamedGraphGetRequestV1, NamedGraphsGetRequestV1, OntologyResponderRequestV1, ResourceTypeGetRequestV1}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
  * Provides a spray-routing function for API routes that deal with resource types.
  */
object ResourceTypesRouteV1 extends Authenticator {

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        def makeResourceTypeRequestMessage(resourceTypeIri: String, userProfile: UserProfileV1): OntologyResponderRequestV1 = {

            ResourceTypeGetRequestV1(resourceTypeIri, userProfile)
        }

        path("v1" / "resourcetypes" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        // TODO: Check that this is the IRI of a resource type and not just any IRI
                        val resourceTypeIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid resource type IRI: $iri"))
                        makeResourceTypeRequestMessage(iri, userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "resourcetypes") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap

                        val vocabulary = params.getOrElse("vocabulary", throw BadRequestException("Required param vocabulary is missing"))
                        val namedGraphIri = InputValidation.toIri(vocabulary, () => throw BadRequestException(s"Invalid vocabulary IRI: $vocabulary"))
                        ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "vocabularies") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        NamedGraphsGetRequestV1(userProfile)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        }


    }
}