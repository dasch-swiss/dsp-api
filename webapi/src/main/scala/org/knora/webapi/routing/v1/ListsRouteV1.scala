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
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContext

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object ListsRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("v1" / "hlists" / Segment) { iri =>
            get {
                requestContext =>

                    val requestMessageFuture = for {
                            userProfile <- getUserADM(requestContext).map(_.asUserProfileV1)
                            listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                            requestMessage = requestContext.request.uri.query().get("reqtype") match {
                              case Some("node") => NodePathGetRequestV1(listIri, userProfile)
                              case Some(reqtype) => throw BadRequestException(s"Invalid reqtype: $reqtype")
                              case None => HListGetRequestV1(listIri, userProfile)
                          }
                        } yield requestMessage


                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessageFuture,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
        path("v1" / "selections" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessageFuture = for {
                            userProfile <- getUserADM(requestContext).map(_.asUserProfileV1)
                            selIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                            requestMessage = requestContext.request.uri.query().get("reqtype") match {
                            case Some("node") => NodePathGetRequestV1(selIri, userProfile)
                            case Some(reqtype) => throw BadRequestException(s"Invalid reqtype: $reqtype")
                            case None => SelectionGetRequestV1(selIri, userProfile)
                        }
                    } yield requestMessage

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessageFuture,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }
}
