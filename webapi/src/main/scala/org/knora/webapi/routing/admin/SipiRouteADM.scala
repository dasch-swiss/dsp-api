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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.BadRequestException
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV1}

/**
  * Provides a routing function for the API that Sipi connects to.
  */
class SipiRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    /**
      * A routing function for the API that Sipi connects to.
      */
    def knoraApiPath: Route = {

        path("admin" / "files" / Segments(2)) { projectIDAndFile: Seq[String] =>
            get {
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                        projectID = stringFormatter.validateProjectShortcode(projectIDAndFile.head, throw BadRequestException(s"Invalid project ID: '${projectIDAndFile.head}'"))
                        filename = stringFormatter.toSparqlEncodedString(projectIDAndFile(1), throw BadRequestException(s"Invalid filename: '${projectIDAndFile(1)}'"))
                    } yield SipiFileInfoGetRequestADM(projectID = projectID, filename = filename, requestingUser = requestingUser)

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
