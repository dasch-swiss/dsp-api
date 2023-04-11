/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1

/**
 * Provides API routes that deal with lists.
 */
final case class ListsRouteV1()(implicit
  val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) {

  def makeRoute: Route = {

    val stringFormatter = StringFormatter.getGeneralInstance

    path("v1" / "hlists" / Segment) { iri =>
      get { requestContext =>
        val requestTask = for {
          user <- RouteUtilV1.getUserProfileV1(requestContext)
          task <- makeGetHierarchicalListMessage(iri, user, requestContext)
        } yield task
        RouteUtilV1.runJsonRouteZ(requestTask, requestContext)
      }
    } ~
      path("v1" / "selections" / Segment) { iri =>
        get { requestContext =>
          val requestMessageFuture = for {
            user <- RouteUtilV1.getUserProfileV1(requestContext)
            task <- makeGetFlatListMessage(iri, user, requestContext)
          } yield task
          RouteUtilV1.runJsonRouteZ(requestMessageFuture, requestContext)
        }
      }
  }

  private def makeGetHierarchicalListMessage(
    iri: String,
    userProfile: UserProfileV1,
    requestContext: RequestContext
  ): ZIO[StringFormatter with MessageRelay, Throwable, ListsResponderRequestV1] =
    for {
      listIri <- RouteUtilV1.validateAndEscapeIri(iri, s"Invalid param list IRI: $iri")
      requestMessage <- requestContext.request.uri.query().get("reqtype") match {
                          case Some("node")  => ZIO.succeed(NodePathGetRequestV1(listIri, userProfile))
                          case Some(reqtype) => ZIO.fail(BadRequestException(s"Invalid reqtype: $reqtype"))
                          case None          => ZIO.succeed(HListGetRequestV1(listIri, userProfile))
                        }
    } yield requestMessage

  private def makeGetFlatListMessage(
    iri: String,
    userProfile: UserProfileV1,
    requestContext: RequestContext
  ): ZIO[StringFormatter with MessageRelay, Throwable, ListsResponderRequestV1] =
    for {
      selIri <- RouteUtilV1.validateAndEscapeIri(iri, s"Invalid param list IRI: $iri")
      requestMessage <- requestContext.request.uri.query().get("reqtype") match {
                          case Some("node")  => ZIO.succeed(NodePathGetRequestV1(selIri, userProfile))
                          case Some(reqtype) => ZIO.fail(BadRequestException(s"Invalid reqtype: $reqtype"))
                          case None          => ZIO.succeed(HListGetRequestV1(selIri, userProfile))
                        }
    } yield requestMessage
}
