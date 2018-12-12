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

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContext

/**
  * Provides a spray-routing function for API routes that deal with resource types.
  */
object ResourceTypesRouteV1 extends Authenticator {
    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("v1" / "resourcetypes" / Segment) { iri =>
            get {
                requestContext =>

                    val requestMessage =  for {
                        userProfile <- getUserADM(requestContext)

                        // TODO: Check that this is the IRI of a resource type and not just any IRI
                        resourceTypeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid resource class IRI: $iri"))


                    } yield ResourceTypeGetRequestV1(resourceTypeIri, userProfile)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "resourcetypes") {
            get {
                requestContext =>

                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        params = requestContext.request.uri.query().toMap

                        vocabularyId = params.getOrElse("vocabulary", throw BadRequestException("Required param vocabulary is missing"))

                        namedGraphIri = vocabularyId match {
                            case "0" => None // if param vocabulary is set to 0, query all named graphs
                            case other => Some(stringFormatter.validateAndEscapeIri(vocabularyId, throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId")))
                        }

                    } yield ResourceTypesForNamedGraphGetRequestV1(namedGraphIri, userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "propertylists") {
            get {
                requestContext =>

                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        params = requestContext.request.uri.query().toMap

                        vocabularyId: Option[String] = params.get("vocabulary")
                        resourcetypeId: Option[String] = params.get("restype")

                        // either the vocabulary or the restype param is set, but not both
                        _ = if (vocabularyId.nonEmpty && resourcetypeId.nonEmpty) throw BadRequestException("Both vocabulary and restype params are set, only one is allowed")
                    } yield vocabularyId match {
                        case Some("0") => // 0 means that all named graphs should be queried
                            PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userADM = userADM)
                        case Some(vocId) =>
                            val namedGraphIri = stringFormatter.validateAndEscapeIri(vocId, throw BadRequestException(s"Invalid vocabulary IRI: $vocabularyId"))
                            PropertyTypesForNamedGraphGetRequestV1(namedGraph = Some(namedGraphIri), userADM = userADM)
                        case None => // no vocabulary id given, check for restype
                            resourcetypeId match {
                                case Some(restypeId) => // get property types for given resource type
                                    val resourceClassIri = stringFormatter.validateAndEscapeIri(restypeId, throw BadRequestException(s"Invalid vocabulary IRI: $restypeId"))
                                    PropertyTypesForResourceTypeGetRequestV1(restypeId, userADM)
                                case None => // no params given, get all property types (behaves like vocbulary=0)
                                    PropertyTypesForNamedGraphGetRequestV1(namedGraph = None, userADM = userADM)
                            }
                    }

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "vocabularies") {
            get {
                requestContext =>
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield NamedGraphsGetRequestV1(userADM = userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

            }
        } ~ path("v1" / "vocabularies" / "reload") {
            get {
                requestContext =>
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                    } yield LoadOntologiesRequest(userADM)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "subclasses" / Segment) {
            iri =>
                get {
                    requestContext =>

                        val requestMessage = for {
                            userADM <- getUserADM(requestContext)

                            // TODO: Check that this is the IRI of a resource type and not just any IRI
                            resourceClassIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid resource class IRI: $iri"))
                        } yield SubClassesGetRequestV1(resourceClassIri, userADM)

                        RouteUtilV1.runJsonRouteWithFuture(
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
