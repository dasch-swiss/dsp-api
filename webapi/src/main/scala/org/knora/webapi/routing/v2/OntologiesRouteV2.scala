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

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.messages.v2.responder.ontologymessages.{NamedGraphEntitiesGetRequestV2, NamedGraphsGetRequestV2, PropertyEntitiesGetRequestV2, ResourceClassesGetRequestV2}
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

import scala.language.postfixOps

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object OntologiesRouteV2 extends Authenticator {


    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "ontologies" / "namedgraphs") {
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = NamedGraphsGetRequestV2(userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "namedgraphs" / Segments) { (externalOntologyIris: List[String]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val internalOntologyIris: Set[IRI] = externalOntologyIris.map {
                        (namedGraph: String) =>

                            // translate the given external ontology Iri to an internal ontology Iri
                            val internalOntologyIri = InputValidation.externalOntologyIriApiV2WithValueObjectToInternalOntologyIri(namedGraph, () => throw BadRequestException(s"given named graph $namedGraph is not a valid external ontology Iri"))

                            InputValidation.toIri(internalOntologyIri, () => throw BadRequestException(s"Invalid named graph Iri: '$internalOntologyIri'"))
                    }.toSet

                    val requestMessage = NamedGraphEntitiesGetRequestV2(internalOntologyIris, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "resourceclasses" / Segments) { (externalResourceClassIris: List[String]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val internalResourceClassIris: Set[IRI] = externalResourceClassIris.map {
                        (resourceClassIri: String) =>

                            // translate the given external resource class Iri to an internal Iri
                            val internalResClassIri = InputValidation.externalApiV2WithValueObjectEntityIriToInternalEntityIri(resourceClassIri, () => throw BadRequestException(s"invalid external resource class Iri: $resourceClassIri"))

                            InputValidation.toIri(internalResClassIri, () => throw BadRequestException(s"Invalid resource class Iri: '$internalResClassIri'"))
                    }.toSet

                    val requestMessage = ResourceClassesGetRequestV2(internalResourceClassIris, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "properties" / Segments) { (externalPropertyIris: List[String]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val internalPropertyIris: Set[IRI] = externalPropertyIris.map {
                        (propIri: String) =>

                            // translate the given external property Iri to an internal Iri
                            val internalPropIri = InputValidation.externalApiV2WithValueObjectEntityIriToInternalEntityIri(propIri, () => throw BadRequestException(s"invalid external property Iri: $propIri"))

                            InputValidation.toIri(internalPropIri, () => throw BadRequestException(s"Invalid property Iri: '$internalPropIri'"))
                    }.toSet

                    val requestMessage = PropertyEntitiesGetRequestV2(internalPropertyIris, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
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