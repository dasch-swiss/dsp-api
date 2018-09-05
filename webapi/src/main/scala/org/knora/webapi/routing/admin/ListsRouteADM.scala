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

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.actor.{ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.util.StringFormatter

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */

@Api(value = "lists", produces = "application/json")
@Path("/admin/lists")
class ListsRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator with ListADMJsonProtocol {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    implicit val timeout: Timeout = settings.defaultTimeout
    val responderManager: ActorSelection = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    def knoraApiPath: Route = {


        path("admin" / "lists") {
            get {
                /* return all lists */
                parameters("projectIri".?) { maybeProjectIri: Option[IRI] =>
                    requestContext =>
                        val projectIri = stringFormatter.toOptionalIri(maybeProjectIri, throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri"))

                        val requestMessage: Future[ListsGetRequestADM] = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ListsGetRequestADM(projectIri, requestingUser)

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            post {
                /* create a list */
                entity(as[CreateListApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val requestMessage: Future[ListCreateRequestADM] = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ListCreateRequestADM(
                            createListRequest = apiRequest,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~
        path("admin" / "lists" / Segment) {iri =>
            get {
                /* return a list (a graph with all list nodes) */
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListGetRequestADM(listIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update list */
                throw NotImplementedException("Method not implemented.")
                ???
            } ~
            delete {
                /* delete (deactivate) list */
                throw NotImplementedException("Method not implemented.")
                ???
            }
        } ~
        path("admin" / "lists" / "infos" / Segment) {iri =>
            get {
                /* return information about a list (without children) */
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListInfoGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListInfoGetRequestADM(listIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update list info */
                entity(as[ChangeListInfoApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                        val requestMessage: Future[ListInfoChangeRequestADM] = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ListInfoChangeRequestADM(
                            listIri = listIri,
                            changeListRequest = apiRequest,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~
        path("admin" / "lists" / "nodes" / Segment) {iri =>
            get {
                /* return information about a single node (without children) */
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListNodeInfoGetRequestADM(listIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update list node */
                throw NotImplementedException("Method not implemented.")
                ???
            } ~
            delete {
                /* delete list node */
                throw NotImplementedException("Method not implemented.")
                ???
            }
        }
    }
}
