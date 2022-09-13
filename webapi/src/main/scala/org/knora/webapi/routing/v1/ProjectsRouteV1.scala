/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import dsp.errors.BadRequestException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

class ProjectsRouteV1(routeData: KnoraRouteData, appConfig: AppConfig)
    extends KnoraRoute(routeData, appConfig)
    with Authenticator
    with ProjectV1JsonProtocol {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v1" / "projects") {
      get {
        /* returns all projects */
        requestContext =>
          val requestMessage = for {
            userProfile <- getUserADM(requestContext, appConfig).map(_.asUserProfileV1)
          } yield ProjectsGetRequestV1(
            userProfile = Some(userProfile)
          )

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            appActor,
            log
          )
      }
    } ~ path("v1" / "projects" / Segment) { value =>
      get {
        /* returns a single project identified either through iri or shortname */
        parameters("identifier" ? "iri") { identifier: String => requestContext =>
          val requestMessage = if (identifier != "iri") { // identify project by shortname.
            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
            for {
              userProfile <- getUserADM(requestContext, appConfig).map(_.asUserProfileV1)
            } yield ProjectInfoByShortnameGetRequestV1(
              shortname = shortNameDec,
              userProfileV1 = Some(userProfile)
            )
          } else { // identify project by iri. this is the default case.
            val checkedProjectIri =
              stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid project IRI $value"))
            for {
              userProfile <- getUserADM(requestContext, appConfig).map(_.asUserProfileV1)
            } yield ProjectInfoByIRIGetRequestV1(
              iri = checkedProjectIri,
              userProfileV1 = Some(userProfile)
            )
          }

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            appActor,
            log
          )
        }
      }
    }
}
