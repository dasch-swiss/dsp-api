/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1
import org.knora.webapi.messages.StringFormatter
import akka.http.scaladsl.server.RequestContext

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
          val requestMessage = getProjectMessage(value, identifier, requestContext)
          RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
        }
      }
    }

  private def getProjectMessage(
    value: String,
    identifier: String,
    requestContext: RequestContext
  ): ZIO[Authenticator with StringFormatter, Throwable, ProjectsResponderRequestV1] =
    if (identifier != "iri") {
      val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
      for {
        userProfile <- RouteUtilV1.getUserProfileV1(requestContext)
      } yield ProjectInfoByShortnameGetRequestV1(
        shortname = shortNameDec,
        userProfileV1 = Some(userProfile)
      )
    } else {
      for {
        checkedProjectIri <- RouteUtilV1.validateAndEscapeIri(value, s"Invalid project IRI $value")
        userProfile       <- RouteUtilV1.getUserProfileV1(requestContext)
      } yield ProjectInfoByIRIGetRequestV1(
        iri = checkedProjectIri,
        userProfileV1 = Some(userProfile)
      )
    }
}
