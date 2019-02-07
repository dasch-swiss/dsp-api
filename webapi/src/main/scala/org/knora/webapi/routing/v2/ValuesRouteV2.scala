/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}

import scala.concurrent.Future

/**
  * Provides a routing function for API v2 routes that deal with values.
  */
class ValuesRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    def knoraApiPath: Route = {

        // #post-value-parse-jsonld
        path("v2" / "values") {
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)
                        // #post-value-parse-jsonld

                        // #post-value-create-message
                        val requestMessageFuture: Future[CreateValueRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                                requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage
                        // #post-value-create-message

                        // #specify-response-schema
                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            ApiV2WithValueObjects
                        )
                        // #specify-response-schema
                    }
                }
            } ~ put {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessageFuture: Future[UpdateValueRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestMessage: UpdateValueRequestV2 <- UpdateValueRequestV2.fromJsonLD(
                                requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            ApiV2WithValueObjects
                        )
                    }
                }
            }
        } ~ path("v2" / "values" / "delete" ) {
            post {
                entity(as[String]) { jsonRequest =>
                    requestContext => {
                        val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                        val requestMessageFuture: Future[DeleteValueRequestV2] = for {
                            requestingUser <- getUserADM(requestContext)
                            requestMessage: DeleteValueRequestV2 <- DeleteValueRequestV2.fromJsonLD(
                                requestDoc,
                                apiRequestID = UUID.randomUUID,
                                requestingUser = requestingUser,
                                responderManager = responderManager,
                                storeManager = storeManager,
                                settings = settings,
                                log = log
                            )
                        } yield requestMessage

                        RouteUtilV2.runRdfRouteWithFuture(
                            requestMessageFuture,
                            requestContext,
                            settings,
                            responderManager,
                            log,
                            ApiV2WithValueObjects
                        )
                    }
                }
            }
        }
    }
}
