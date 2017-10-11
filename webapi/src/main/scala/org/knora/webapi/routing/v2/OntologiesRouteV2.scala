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
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassesGetRequestV2, NamedGraphEntitiesGetRequestV2, NamedGraphsGetRequestV2, PropertyEntitiesGetRequestV2}
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.InputValidation
import org.knora.webapi._

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object OntologiesRouteV2 extends Authenticator {
    val ALL_LANGUAGES = "allLanguages"

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("ontology" / Segments) { (segments: List[String]) =>
            get {
                requestContext => {
                    // This is the route used to dereference an actual ontology IRI. It assumes that it was accessed via
                    // a URI starting with http://api.knora.org. To make this work on localhost on macOS, add the following to
                    // /etc/hosts:
                    //
                    // 127.0.0.1    api.knora.org
                    //
                    // Then run webapi/scripts/macOS-port-forwarding.sh to forward port 80 to port 3333. For details, see
                    // <https://salferrarello.com/mac-pfctl-port-forwarding/>.

                    val userProfile = getUserProfileV1(requestContext)

                    val requestedOntology: IRI = OntologyConstants.KnoraApi.ApiOntologyHostname + requestContext.request.uri.path
                    val responseSchema: ApiV2Schema = InputValidation.getOntologyApiSchema(requestedOntology, () => throw BadRequestException(s"Invalid external ontology IRI: $requestedOntology"))
                    val ontologyForResponder = InputValidation.requestedOntologyToOntologyForResponder(requestedOntology)
                    val ontologiesForResponder: Set[IRI] = Set(ontologyForResponder)

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = InputValidation.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = NamedGraphEntitiesGetRequestV2(
                        namedGraphIris = ontologiesForResponder,
                        allLanguages = allLanguages,
                        userProfile = userProfile
                    )

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = responseSchema
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "namedgraphs") {
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

                    val ontologiesAndSchemas: Set[(IRI, ApiV2Schema)] = externalOntologyIris.map {
                        (namedGraph: String) =>
                            val schema = InputValidation.getOntologyApiSchema(namedGraph, () => throw BadRequestException(s"Invalid external ontology IRI: $namedGraph"))
                            val ontologyForResponder = InputValidation.requestedOntologyToOntologyForResponder(namedGraph)
                            (ontologyForResponder, schema)
                    }.toSet

                    val (ontologiesForResponder: Set[IRI], schemas: Set[ApiV2Schema]) = ontologiesAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = InputValidation.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = NamedGraphEntitiesGetRequestV2(
                        namedGraphIris = ontologiesForResponder,
                        allLanguages = allLanguages,
                        userProfile = userProfile
                    )

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = responseSchema
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "classes" / Segments) { (externalResourceClassIris: List[String]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val classesAndSchemas: Set[(IRI, ApiV2Schema)] = externalResourceClassIris.map {
                        (classIri: String) =>
                            // Find out what schema the class IRI belongs to.
                            val schema = InputValidation.getEntityApiSchema(classIri, () => throw BadRequestException(s"Invalid external class IRI: $classIri"))
                            val classForResponder = InputValidation.requestedEntityToEntityForResponder(classIri)

                            (classForResponder, schema)
                    }.toSet

                    val (classesForResponder: Set[IRI], schemas: Set[ApiV2Schema]) = classesAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = InputValidation.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = ClassesGetRequestV2(
                        resourceClassIris = classesForResponder,
                        allLanguages = allLanguages,
                        userProfile = userProfile
                    )

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "properties" / Segments) { (externalPropertyIris: List[String]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val propsAndSchemas: Set[(IRI, ApiV2Schema)] = externalPropertyIris.map {
                        (propIri: String) =>
                            // Find out what schema the property IRI belongs to.
                            val schema = InputValidation.getEntityApiSchema(propIri, () => throw BadRequestException(s"Invalid external property IRI: $propIri"))
                            val propForResponder = InputValidation.requestedEntityToEntityForResponder(propIri)
                            (propForResponder, schema)
                    }.toSet

                    val (propsForResponder: Set[IRI], schemas: Set[ApiV2Schema]) = propsAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = InputValidation.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = PropertyEntitiesGetRequestV2(
                        propertyIris = propsForResponder,
                        allLanguages = allLanguages,
                        userProfile = userProfile
                    )

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = responseSchema
                    )
                }
            }
        }
    }
}