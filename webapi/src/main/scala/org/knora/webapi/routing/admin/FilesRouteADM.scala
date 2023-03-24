/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides a routing function for the API that Sipi connects to.
 */
final case class FilesRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  /**
   * A routing function for the API that Sipi connects to.
   */
  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("admin" / "files" / Segments(2)) { projectIDAndFile: Seq[String] =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext)
          projectID = stringFormatter.validateProjectShortcode(
                        projectIDAndFile.head,
                        throw BadRequestException(s"Invalid project ID: '${projectIDAndFile.head}'")
                      )
          filename = stringFormatter.toSparqlEncodedString(
                       projectIDAndFile(1),
                       throw BadRequestException(s"Invalid filename: '${projectIDAndFile(1)}'")
                     )
          _ = log.info(s"/admin/files route called for filename $filename by user: ${requestingUser.id}")
        } yield SipiFileInfoGetRequestADM(
          projectID = projectID,
          filename = filename,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRouteF(requestMessage, requestContext)
      }
    }
}
