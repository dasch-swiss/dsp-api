/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 *
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.knora.webapi.routing.admin

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.admin.responder.permissionsmessages.{AdministrativePermissionForProjectGroupGetRequestADM, PermissionType}
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}

import scala.concurrent.ExecutionContextExecutor

object PermissionsRouteADM extends Authenticator {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "permissions" / Segment / Segment) { (projectIri, groupIri) =>
            get {
                requestContext =>
                    val requestingUser = getUserADM(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val permissionType = params.getOrElse("permissionType", PermissionType.AP)
                    val requestMessage = permissionType match {
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
