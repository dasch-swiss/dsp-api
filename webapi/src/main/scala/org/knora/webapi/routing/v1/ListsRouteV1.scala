/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import dsp.errors.BadRequestException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

/**
 * Provides API routes that deal with lists.
 */
class ListsRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute: Route = {

    val stringFormatter = StringFormatter.getGeneralInstance

    path("v1" / "hlists" / Segment) { iri =>
      get { requestContext =>
        val requestMessageFuture = for {
          userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
          listIri = stringFormatter.validateAndEscapeIri(
                      iri,
                      throw BadRequestException(s"Invalid param list IRI: $iri")
                    )

          requestMessage = requestContext.request.uri.query().get("reqtype") match {
                             case Some("node")  => NodePathGetRequestV1(listIri, userProfile)
                             case Some(reqtype) => throw BadRequestException(s"Invalid reqtype: $reqtype")
                             case None          => HListGetRequestV1(listIri, userProfile)
                           }
        } yield requestMessage

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessageFuture,
          requestContext,
          appActor,
          log
        )
      }
    } ~
      path("v1" / "selections" / Segment) { iri =>
        get { requestContext =>
          val requestMessageFuture = for {
            userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
            selIri = stringFormatter.validateAndEscapeIri(
                       iri,
                       throw BadRequestException(s"Invalid param list IRI: $iri")
                     )

            requestMessage = requestContext.request.uri.query().get("reqtype") match {
                               case Some("node")  => NodePathGetRequestV1(selIri, userProfile)
                               case Some(reqtype) => throw BadRequestException(s"Invalid reqtype: $reqtype")
                               case None          => SelectionGetRequestV1(selIri, userProfile)
                             }
          } yield requestMessage

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessageFuture,
            requestContext,
            appActor,
            log
          )
        }
      }
  }
}
