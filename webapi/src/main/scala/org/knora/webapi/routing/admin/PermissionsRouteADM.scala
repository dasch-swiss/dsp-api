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

package org.knora.webapi.routing.admin

import akka.actor.{ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.permissionsmessages.{AdministrativePermissionForProjectGroupGetRequestADM, PermissionType}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.{KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContext

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class PermissionsRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    implicit val timeout: Timeout = settings.defaultTimeout
    val responderManager: ActorSelection = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

    def knoraApiPath: Route = {

        path("admin" / "permissions" / Segment / Segment) { (projectIri, groupIri) =>
            get {
                requestContext =>
                    val params = requestContext.request.uri.query().toMap
                    val permissionType = params.getOrElse("permissionType", PermissionType.AP)
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield permissionType match {
                        case _ => AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)
                    }

                    RouteUtilADM.runJsonRoute(
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
