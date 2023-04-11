/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._

import java.util.UUID

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV1

/**
 * Provides a spray-routing function for API routes that deal with lists.
 */
final case class UsersRouteV1()(implicit r: Runtime[Authenticator with MessageRelay with StringFormatter]) {

  def makeRoute: Route =
    path("v1" / "users") {
      get(ctx => RouteUtilV1.runJsonRouteZ(RouteUtilV1.getUserProfileV1(ctx).map(UsersGetRequestV1), ctx))
    } ~
      path("v1" / "users" / Segment) { value =>
        get {
          /* return a single user identified by iri or email */
          parameters("identifier" ? "iri") { (identifier: String) => requestContext =>
            /* check if email or iri was supplied */
            val requestMessage = if (identifier == "email") {
              RouteUtilV1
                .getUserProfileV1(requestContext)
                .map(UserProfileByEmailGetRequestV1(value, UserProfileTypeV1.RESTRICTED, _))
            } else {
              for {
                userProfile <- RouteUtilV1.getUserProfileV1(requestContext)
                userIri     <- RouteUtilV1.validateAndEscapeIri(value, "Invalid user IRI")
              } yield UserProfileByIRIGetRequestV1(userIri, UserProfileTypeV1.RESTRICTED, userProfile)
            }
            RouteUtilV1.runJsonRouteZ(requestMessage, requestContext)
          }
        }
      } ~
      path("v1" / "users" / "projects" / Segment) { userIri =>
        get {
          /* get user's project memberships */
          requestContext =>
            val msg = validateIriAndGetProfileUuid(userIri, requestContext)
              .map(it => UserProjectMembershipsGetRequestV1(it.userIri, it.userProfile, it.uuid))
            RouteUtilV1.runJsonRouteZ(msg, requestContext)
        }
      } ~
      path("v1" / "users" / "projects-admin" / Segment) { userIri =>
        get {
          /* get user's project admin memberships */
          requestContext =>
            val msg = validateIriAndGetProfileUuid(userIri, requestContext)
              .map(it => UserProjectAdminMembershipsGetRequestV1(it.userIri, it.userProfile, it.uuid))
            RouteUtilV1.runJsonRouteZ(msg, requestContext)
        }
      } ~
      path("v1" / "users" / "groups" / Segment) { userIri =>
        get {
          /* get user's group memberships */
          requestContext =>
            val msg = validateIriAndGetProfileUuid(userIri, requestContext)
              .map(it => UserGroupMembershipsGetRequestV1(it.userIri, it.userProfile, it.uuid))
            RouteUtilV1.runJsonRouteZ(msg, requestContext)
        }
      }
  private case class UserIriProfileUuid(userIri: String, userProfile: UserProfileV1, uuid: UUID)

  private def validateIriAndGetProfileUuid(
    iri: String,
    requestContext: RequestContext
  ): ZIO[StringFormatter with Authenticator, Throwable, UserIriProfileUuid] =
    for {
      userProfile <- RouteUtilV1.getUserProfileV1(requestContext)
      userIri     <- RouteUtilV1.validateAndEscapeIri(iri, "Invalid user IRI")
      uuid        <- RouteUtilV1.randomUuid()
    } yield UserIriProfileUuid(userIri, userProfile, uuid)
}
