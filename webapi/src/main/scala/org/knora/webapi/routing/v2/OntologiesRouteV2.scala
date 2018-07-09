/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
  * Provides a routing function for API v2 routes that deal with ontologies.
  */
object OntologiesRouteV2 extends Authenticator {
    private val ALL_LANGUAGES = "allLanguages"
    private val LAST_MODIFICATION_DATE = "lastModificationDate"

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val responderManager = system.actorSelection("/user/responderManager")

        path("ontology" / Segments) { _: List[String] =>
            get {
                requestContext => {
                    // This is the route used to dereference an actual ontology IRI. If the URL path looks like it
                    // belongs to a built-in API ontology (which has to contain "knora-api"), prefix it with
                    // http://api.knora.org to get the ontology IRI. Otherwise, if it looks like it belongs to a
                    // project-specific API ontology, prefix it with settings.knoraApiHttpBaseUrl to get the ontology
                    // IRI.

                    val requestingUser = getUserADM(requestContext)
                    val urlPath = requestContext.request.uri.path.toString

                    val requestedOntologyStr: IRI = if (stringFormatter.isBuiltInApiV2OntologyUrlPath(urlPath)) {
                        OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath
                    } else if (stringFormatter.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
                        settings.externalKnoraApiBaseUrl + urlPath
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
                        ontologyIri = requestedOntology,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
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
                    val requestingUser = getUserADM(requestContext)

                    val requestMessage = OntologyMetadataGetRequestV2(requestingUser = requestingUser)

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = ApiV2WithValueObjects
                    )
                }
            } ~ put {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: ChangeOntologyMetadataRequestV2 = ChangeOntologyMetadataRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
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
                    val requestingUser = getUserADM(requestContext)
                    val validatedProjectIris = projectIris.map(iri => iri.toSmartIriWithErr(throw BadRequestException(s"Invalid project IRI: $iri"))).toSet
                    val requestMessage = OntologyMetadataGetRequestV2(projectIris = validatedProjectIris, requestingUser = requestingUser)

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = ApiV2WithValueObjects
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "allentities" / Segment) { externalOntologyIriStr: IRI =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val requestedOntologyIri = externalOntologyIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr"))

                    val responseSchema = requestedOntologyIri.getOntologySchema match {
                        case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                        case _ => throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIriStr")
                    }

                    val params: Map[String, String] = requestContext.request.uri.query().toMap
                    val allLanguagesStr = params.get(ALL_LANGUAGES)
                    val allLanguages = stringFormatter.optionStringToBoolean(params.get(ALL_LANGUAGES), throw BadRequestException(s"Invalid boolean for $ALL_LANGUAGES: $allLanguagesStr"))

                    val requestMessage = OntologyEntitiesGetRequestV2(
                        ontologyIri = requestedOntologyIri,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = responseSchema
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "classes") {
            post {
                // Create a new class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: CreateClassRequestV2 = CreateClassRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            responseSchema = ApiV2WithValueObjects
                        )
                    }
                }
            } ~ put {
                // Change the labels or comments of a class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: ChangeClassLabelsOrCommentsRequestV2 = ChangeClassLabelsOrCommentsRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
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
        } ~ path("v2" / "ontologies" / "cardinalities") {
            post {
                // Add cardinalities to a class.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: AddCardinalitiesToClassRequestV2 = AddCardinalitiesToClassRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            responseSchema = ApiV2WithValueObjects
                        )
                    }
                }
            } ~ put {
                // Change a class's cardinalities.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: ChangeCardinalitiesRequestV2 = ChangeCardinalitiesRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
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
        } ~ path("v2" / "ontologies" / "classes" / Segments) { externalResourceClassIris: List[IRI] =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val classesAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalResourceClassIris.map {
                        (classIriStr: IRI) =>
                            val requestedClassIri: SmartIri = classIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid class IRI: $classIriStr"))

                            if (!requestedClassIri.isKnoraApiV2EntityIri) {
                                throw BadRequestException(s"Invalid class IRI: $classIriStr")
                            }

                            val schema = requestedClassIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid class IRI: $classIriStr")
                            }

                            (requestedClassIri, schema)
                    }.toSet

                    val (classesForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = classesAndSchemas.unzip

                    if (classesForResponder.map(_.getOntologyFromEntity).size != 1) {
                        throw BadRequestException(s"Only one ontology may be queried per request")
                    }

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
                        classIris = classesForResponder,
                        allLanguages = allLanguages,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema
                    )
                }
            } ~ delete {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val classIriStr = externalResourceClassIris match {
                        case List(str) => str
                        case _ => throw BadRequestException(s"Only one class can be deleted at a time")
                    }

                    val classIri = classIriStr.toSmartIri

                    if (!classIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                        throw BadRequestException(s"Invalid class IRI for request: $classIriStr")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.toInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessage = DeleteClassRequestV2(
                        classIri = classIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = ApiV2WithValueObjects
                    )
                }
            }
        } ~ path("v2" / "ontologies" / "properties") {
            post {
                // Create a new property.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: CreatePropertyRequestV2 = CreatePropertyRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            responseSchema = ApiV2WithValueObjects
                        )
                    }
                }
            } ~ put {
                // Change the labels or comments of a property.
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: ChangePropertyLabelsOrCommentsRequestV2 = ChangePropertyLabelsOrCommentsRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
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
        } ~ path("v2" / "ontologies" / "properties" / Segments) { externalPropertyIris: List[IRI] =>
            get {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val propsAndSchemas: Set[(SmartIri, ApiV2Schema)] = externalPropertyIris.map {
                        (propIriStr: IRI) =>
                            val requestedPropIri: SmartIri = propIriStr.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $propIriStr"))

                            if (!requestedPropIri.isKnoraApiV2EntityIri) {
                                throw BadRequestException(s"Invalid property IRI: $propIriStr")
                            }

                            val schema = requestedPropIri.getOntologySchema match {
                                case Some(apiV2Schema: ApiV2Schema) => apiV2Schema
                                case _ => throw BadRequestException(s"Invalid property IRI: $propIriStr")
                            }

                            (requestedPropIri, schema)
                    }.toSet

                    val (propsForResponder: Set[SmartIri], schemas: Set[ApiV2Schema]) = propsAndSchemas.unzip

                    if (propsForResponder.map(_.getOntologyFromEntity).size != 1) {
                        throw BadRequestException(s"Only one ontology may be queried per request")
                    }

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
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = responseSchema
                    )
                }
            } ~ delete {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)

                    val propertyIriStr = externalPropertyIris match {
                        case List(str) => str
                        case _ => throw BadRequestException(s"Only one property can be deleted at a time")
                    }

                    val propertyIri = propertyIriStr.toSmartIri

                    if (!propertyIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                        throw BadRequestException(s"Invalid property IRI for request: $propertyIri")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.toInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessage = DeletePropertyRequestV2(
                        propertyIri = propertyIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        responseSchema = ApiV2WithValueObjects
                    )
                }
            }
        } ~ path("v2" / "ontologies") {
            // Create a new, empty ontology.
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestingUser = getUserADM(requestContext)
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessage: CreateOntologyRequestV2 = CreateOntologyRequestV2.fromJsonLD(
                            jsonLDDocument = requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser
                        )

                        RouteUtilV2.runRdfRoute(
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
        } ~ path ("v2" / "ontologies" / Segment) { ontologyIriStr =>
            delete {
                requestContext => {
                    val requestingUser = getUserADM(requestContext)
                    val ontologyIri = ontologyIriStr.toSmartIri

                    if (!ontologyIri.isKnoraOntologyIri || ontologyIri.isKnoraBuiltInDefinitionIri || !ontologyIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
                        throw BadRequestException(s"Invalid ontology IRI for request: $ontologyIri")
                    }

                    val lastModificationDateStr = requestContext.request.uri.query().toMap.getOrElse(LAST_MODIFICATION_DATE, throw BadRequestException(s"Missing parameter: $LAST_MODIFICATION_DATE"))
                    val lastModificationDate = stringFormatter.toInstant(lastModificationDateStr, throw BadRequestException(s"Invalid timestamp: $lastModificationDateStr"))

                    val requestMessage = DeleteOntologyRequestV2(
                        ontologyIri = ontologyIri,
                        lastModificationDate = lastModificationDate,
                        apiRequestID = UUID.randomUUID,
                        requestingUser = requestingUser
                    )

                    RouteUtilV2.runRdfRoute(
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