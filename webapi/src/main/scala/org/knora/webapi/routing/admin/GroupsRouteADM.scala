/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

import java.util.UUID
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects.{Description, Name, Selfjoin, Status}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

object GroupsRouteADM {
  val GroupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")
}

/**
 * Provides a routing function for API routes that deal with groups.
 */

@Api(value = "groups", produces = "application/json")
@Path("/admin/groups")
class GroupsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with GroupsADMJsonProtocol {

  import GroupsRouteADM._

  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getGroups(featureFactoryConfig) ~
      createGroup(featureFactoryConfig) ~
      getGroupByIri(featureFactoryConfig) ~
      updateGroup(featureFactoryConfig) ~
      changeGroupStatus(featureFactoryConfig) ~
      deleteGroup(featureFactoryConfig) ~
      getGroupMembers(featureFactoryConfig)

  /**
   * Returns all groups
   */
  private def getGroups(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath) {
    get {
      /* return all groups */
      requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield GroupsGetRequestADM(
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
    }
  }

  /**
   * Creates a group
   */
  private def createGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath) {
    post {
      /* create a new group */
      entity(as[CreateGroupApiRequestADM]) { apiRequest => requestContext =>
        val groupCreatePayloadADM: GroupCreatePayloadADM = GroupCreatePayloadADM.create(
          id = stringFormatter
            .validateAndEscapeOptionalIri(apiRequest.id, throw BadRequestException(s"Invalid custom group IRI")),
          name = Name.create(apiRequest.name).fold(e => throw e, v => v),
          descriptions = Description.create(apiRequest.descriptions).fold(e => throw e, v => v),
          project = stringFormatter
            .validateAndEscapeProjectIri(apiRequest.project, throw BadRequestException(s"Invalid project IRI")),
          status = Status.create(apiRequest.status).fold(e => throw e, v => v),
          selfjoin = Selfjoin.create(apiRequest.selfjoin).fold(e => throw e, v => v)
        )

        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield GroupCreateRequestADM(
          createRequest = groupCreatePayloadADM,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /**
   * Returns a single group identified by IRI.
   */
  private def getGroupByIri(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) {
    value =>
      get {
        /* returns a single group identified through iri */
        requestContext =>
          val checkedGroupIri =
            stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid custom group IRI $value"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield GroupGetRequestADM(
            groupIri = checkedGroupIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
      }
  }

  /**
   * Update basic group information.
   */
  private def updateGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) { value =>
    put {
      /* update a group identified by iri */
      entity(as[ChangeGroupApiRequestADM]) { apiRequest => requestContext =>
        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

        /**
         * The api request is already checked at time of creation.
         * See case class.
         */
        if (apiRequest.status.nonEmpty) {
          throw BadRequestException(
            "The status property is not allowed to be set for this route. Please use the change status route."
          )
        }

        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield GroupChangeRequestADM(
          groupIri = checkedGroupIri,
          changeGroupRequest = apiRequest,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /**
   * Update the group's status.
   */
  private def changeGroupStatus(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(GroupsBasePath / Segment / "status") { value =>
      put {
        /* change the status of a group identified by iri */
        entity(as[ChangeGroupApiRequestADM]) { apiRequest => requestContext =>
          val checkedGroupIri =
            stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

          /**
           * The api request is already checked at time of creation.
           * See case class. Depending on the data sent, we are either
           * doing a general update or status change. Since we are in
           * the status change route, we are only interested in the
           * value of the status property
           */
          if (apiRequest.status.isEmpty) {
            throw BadRequestException("The status property is not allowed to be empty.")
          }

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield GroupChangeStatusRequestADM(
            groupIri = checkedGroupIri,
            changeGroupRequest = apiRequest,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
   * Deletes a group (sets status to false)
   */
  private def deleteGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) { value =>
    delete {
      /* update group status to false */
      requestContext =>
        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield GroupChangeStatusRequestADM(
          groupIri = checkedGroupIri,
          changeGroupRequest = ChangeGroupApiRequestADM(status = Some(false)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
    }
  }

  /**
   * Gets members of single group.
   */
  private def getGroupMembers(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(GroupsBasePath / Segment / "members") { value =>
      get {
        /* returns all members of the group identified through iri */
        requestContext =>
          val checkedGroupIri =
            stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

          val requestMessage = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield GroupMembersGetRequestADM(
            groupIri = checkedGroupIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
      }
    }
}
