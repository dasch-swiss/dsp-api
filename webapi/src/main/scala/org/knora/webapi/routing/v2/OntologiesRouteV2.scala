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
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.util.{SmartIri, StringFormatter}

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
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        path("ontology" / Segments) { (_: List[String]) =>
            get {
                requestContext => {
                    // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
                    // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
                    // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
                    // project-specific API ontology, prefix it with settings.knoraApiHttpBaseUrl to get the ontology
                    // IRI.

                    val userProfile = getUserProfileV1(requestContext)
                    val urlPath = requestContext.request.uri.path.toString

                    val requestedOntologyStr: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
                    } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                        settings.knoraApiHttpBaseUrl + urlPath
                    } else {
                        throw BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath")
                    }

                    val requestedOntology = requestedOntologyStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr"))

                    val responseSchema = requestedOntology.getOntologySchema match {
                        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                        case _ => throw BadRequestException(s"Invalid ontology IRI: $requestedOntologyStr")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = OntologyEntitiesGetRequestV2(
                        ontologyGraphIris = Set(requestedOntology),
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
        } ~ path("v2" / "ontologies" / "metadata") {
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = OntologyMetadataGetRequestV2(userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            } ~ put {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val userProfile = getUserProfileV1(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: ChangeOntologyMetadataRequestV2 = ChangeOntologyMetadataRequestV2.fromJsonLD(
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
        } ~ path("v2" / "ontologies" / "metadata" / Segments) { (projectIris: List[IRI]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)
                    val validatedProjectIris = projectIris.map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri"))).toSet
                    val requestMessage = OntologyMetadataGetRequestV2(projectIris = validatedProjectIris, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "allentities" / Segments) { (externalOntologyIris: List[IRI]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val ontologiesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalOntologyIris.map {
                        (namedGraphStr: IRI) =>
                            val requestedOntologyIri: SmartIri = namedGraphStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $namedGraphStr"))

                            val schema = requestedOntologyIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid ontology IRI: $namedGraphStr")
                            }

                            (requestedOntologyIri, schema)
                    }.toSet

                    val (ontologiesForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = ontologiesAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = OntologyEntitiesGetRequestV2(
                        ontologyGraphIris = ontologiesForResponder,
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
        } ~ path("v2" / "ontologies" / "classes" / Segments) { (externalResourceClassIris: List[IRI]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map {
                        (classIriStr: IRI) =>
                            val requestedClassIri: SmartIri = classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

                            val schema = requestedClassIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid class IRI: $classIriStr")
                            }

                            (requestedClassIri, schema)
                    }.toSet

                    val (classesForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = classesAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

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
        } ~ path("v2" / "ontologies" / "properties") {
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val userProfile = getUserProfileV1(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: CreatePropertyRequestV2 = CreatePropertyRequestV2.fromJsonLD(
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
        } ~ path("v2" / "ontologies" / "properties" / Segments) { (externalPropertyIris: List[IRI]) =>
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map {
                        (propIriStr: IRI) =>
                            val requestedPropIri: SmartIri = propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

                            val schema = requestedPropIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid property IRI: $propIriStr")
                            }

                            (requestedPropIri, schema)
                    }.toSet

                    val (propsForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = propsAndSchemas.unzip

                    // Decide which API schema to use for the response.
                    val responseSchema = if (schemas.size == 1) {
                        schemas.head
                    } else {
                        // The client requested different schemas.
                        throw BadRequestException("The request refers to multiple API schemas")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = PropertiesGetRequestV2(
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