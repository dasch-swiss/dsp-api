/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1
import org.knora.webapi.routing.RouteUtilZ

final case class ProjectsRouteV1()(
  private implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends ProjectV1JsonProtocol {

  def makeRoute: Route =
    path("v1" / "projects") {
      get {
        /* returns all projects */
        requestContext =>
          val requestTask =
            Authenticator.getUserADM(requestContext).map(user => ProjectsGetRequestV1(Some(user.asUserProfileV1)))
          RouteUtilV1.runJsonRouteZ(requestTask, requestContext)
      }
    } ~ path("v1" / "projects" / Segment) { value =>
      get {
        parameters("identifier" ? "iri") { identifier: String => requestContext =>
          val requestMessage =
            if (identifier != "iri") getProjectMessageByShortname(value, requestContext)
            else getProjectMessageByIri(value, requestContext)
          RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
        }
      }
    }

  private def getProjectMessageByIri(
    iri: String,
    requestContext: RequestContext
  ): ZIO[Authenticator with StringFormatter, Throwable, ProjectInfoByIRIGetRequestV1] =
    for {
      checkedProjectIri <- RouteUtilV1.validateAndEscapeIri(iri, s"Invalid project IRI $iri")
      userProfile       <- RouteUtilV1.getUserProfileV1(requestContext)
    } yield ProjectInfoByIRIGetRequestV1(
      iri = checkedProjectIri,
      userProfileV1 = Some(userProfile)
    )

  private def getProjectMessageByShortname(
    shortname: String,
    requestContext: RequestContext
  ): ZIO[Authenticator with StringFormatter, Throwable, ProjectInfoByShortnameGetRequestV1] =
    for {
      shortNameDecoded <- RouteUtilZ.urlDecode(shortname)
      userProfile      <- RouteUtilV1.getUserProfileV1(requestContext)
    } yield ProjectInfoByShortnameGetRequestV1(
      shortname = shortNameDecoded,
      userProfileV1 = Some(userProfile)
    )
}
