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

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}

import scala.concurrent.ExecutionContextExecutor

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
        val stringFormatter = StringFormatter.getInstance

        path("ontology" / Segments) { (segments: List[String]) =>
            get {
                requestContext => {
                    // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
                    // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
                    // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
                    // project-specific API ontology, prefix it with settings.knoraApiHttpBaseUrl to get the ontology
                    // IRI.

                    val userProfile = getUserProfileV1(requestContext)
                    val urlPath = requestContext.request.uri.path.toString

                    val requestedOntology: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
                    } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                        settings.knoraApiHttpBaseUrl + urlPath
                    } else {
                        throw BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath")
                    }

                    val responseSchema: ApiV2Schema = stringFormatter.getOntologyApiSchema(requestedOntology, () => throw BadRequestException(s"Invalid or unknown external ontology IRI: $requestedOntology"))
                    val ontologyForResponder = stringFormatter.requestedOntologyToOntologyForResponder(requestedOntology)
                    val ontologiesForResponder: Set[IRI] = Set(ontologyForResponder)

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = NamedGraphEntitiesGetRequestV2(
                        namedGraphIris = ontologiesForResponder,
                        responseSchema = responseSchema,
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
                            val schema = stringFormatter.getOntologyApiSchema(namedGraph, () => throw BadRequestException(s"Invalid or unknown external ontology IRI: $namedGraph"))
                            val ontologyForResponder = stringFormatter.requestedOntologyToOntologyForResponder(namedGraph)
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
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = NamedGraphEntitiesGetRequestV2(
                        namedGraphIris = ontologiesForResponder,
                        responseSchema = responseSchema,
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
                            val schema = stringFormatter.getEntityApiSchema(classIri, () => throw BadRequestException(s"Invalid or unknown external class IRI: $classIri"))
                            val classForResponder = stringFormatter.requestedEntityToEntityForResponder(classIri)

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
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = ClassesGetRequestV2(
                        resourceClassIris = classesForResponder,
                        responseSchema = responseSchema,
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
                            val schema = stringFormatter.getEntityApiSchema(propIri, () => throw BadRequestException(s"Invalid or unknown external property IRI: $propIri"))
                            val propForResponder = stringFormatter.requestedEntityToEntityForResponder(propIri)
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
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), () => throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

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
        } ~ path("v2" / "ontologies") {
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val userProfile = getUserProfileV1(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: CreateOntologyRequestV2 = CreateOntologyRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            userProfile = userProfile
                        )

                        RouteUtilV2.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            responseSchema = ApiV2WithValueObjects
                        )
                    }
                }
            }
        }
    }
}