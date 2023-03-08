/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator

import java.util.UUID

import dsp.errors.BadRequestException
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

/**
 * Provides a spray-routing function for API routes that deal with lists.
 */
class UsersRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  private val schemes = Array("http", "https")
  new UrlValidator(schemes)

  /**
   * Returns the route.
   */
  override def makeRoute: Route = {

    path("v1" / "users") {
      get {
        /* return all users */
        requestContext =>
          val requestMessage = for {
            userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
          } yield UsersGetRequestV1(userProfile)

          RouteUtilV1.runJsonRouteWithFuture(
            requestMessage,
            requestContext,
            appActor,
            log
          )
      }
    } ~
      path("v1" / "users" / Segment) { value =>
        get {
          /* return a single user identified by iri or email */
          parameters("identifier" ? "iri") { (identifier: String) => requestContext =>
            /* check if email or iri was supplied */
            val requestMessage = if (identifier == "email") {
              for {
                userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
              } yield UserProfileByEmailGetRequestV1(
                email = value,
                userProfileType = UserProfileTypeV1.RESTRICTED,
                userProfile = userProfile
              )
            } else {
              for {
                userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
                userIri = stringFormatter.validateAndEscapeIri(
                            value,
                            throw BadRequestException(s"Invalid user IRI $value")
                          )
              } yield UserProfileByIRIGetRequestV1(
                userIri = userIri,
                userProfileType = UserProfileTypeV1.RESTRICTED,
                userProfile = userProfile
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
      } ~
      path("v1" / "users" / "projects" / Segment) { userIri =>
        get {
          /* get user's project memberships */
          requestContext =>
            val requestMessage = for {
              userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
              checkedUserIri = stringFormatter.validateAndEscapeIri(
                                 userIri,
                                 throw BadRequestException(s"Invalid user IRI $userIri")
                               )
            } yield UserProjectMembershipsGetRequestV1(
              userIri = checkedUserIri,
              userProfileV1 = userProfile,
              apiRequestID = UUID.randomUUID()
            )

            RouteUtilV1.runJsonRouteWithFuture(
              requestMessage,
              requestContext,
              appActor,
              log
            )
        }
      } ~
      path("v1" / "users" / "projects-admin" / Segment) { userIri =>
        get {
          /* get user's project admin memberships */
          requestContext =>
            val requestMessage = for {
              userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
              checkedUserIri = stringFormatter.validateAndEscapeIri(
                                 userIri,
                                 throw BadRequestException(s"Invalid user IRI $userIri")
                               )
            } yield UserProjectAdminMembershipsGetRequestV1(
              userIri = checkedUserIri,
              userProfileV1 = userProfile,
              apiRequestID = UUID.randomUUID()
            )

            RouteUtilV1.runJsonRouteWithFuture(
              requestMessage,
              requestContext,
              appActor,
              log
            )
        }
      } ~
      path("v1" / "users" / "groups" / Segment) { userIri =>
        get {
          /* get user's group memberships */
          requestContext =>
            val requestMessage = for {
              userProfile <- getUserADM(requestContext, routeData.appConfig).map(_.asUserProfileV1)
              checkedUserIri = stringFormatter.validateAndEscapeIri(
                                 userIri,
                                 throw BadRequestException(s"Invalid user IRI $userIri")
                               )
            } yield UserGroupMembershipsGetRequestV1(
              userIri = checkedUserIri,
              userProfileV1 = userProfile,
              apiRequestID = UUID.randomUUID()
            )

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
