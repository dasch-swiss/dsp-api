/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
import zio.prelude.Validation

import java.util.UUID
import javax.ws.rs.Path
import dsp.valueobjects.Iri._
import dsp.valueobjects.Group._

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
      getGroup(featureFactoryConfig) ~
      getGroupMembers(featureFactoryConfig) ~
      createGroup(featureFactoryConfig) ~
      updateGroup(featureFactoryConfig) ~
      changeGroupStatus(featureFactoryConfig) ~
      deleteGroup(featureFactoryConfig)

  /**
   * Returns all groups.
   */
  private def getGroups(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath) {
    get { requestContext =>
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
   * Returns a single group identified by IRI.
   */
  private def getGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) { value =>
    get { requestContext =>
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
   * Returns all members of single group.
   */
  private def getGroupMembers(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(GroupsBasePath / Segment / "members") { value =>
      get { requestContext =>
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

  /**
   * Creates a group.
   */
  private def createGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath) {
    post {
      entity(as[CreateGroupApiRequestADM]) { apiRequest => requestContext =>
        val id: Validation[Throwable, Option[GroupIRI]]            = GroupIRI.make(apiRequest.id)
        val name: Validation[Throwable, GroupName]                 = GroupName.make(apiRequest.name)
        val descriptions: Validation[Throwable, GroupDescriptions] = GroupDescriptions.make(apiRequest.descriptions)
        val project: Validation[Throwable, ProjectIRI]             = ProjectIRI.make(apiRequest.project)
        val status: Validation[Throwable, GroupStatus]             = GroupStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, GroupSelfJoin]         = GroupSelfJoin.make(apiRequest.selfjoin)

        val validatedGroupCreatePayload: Validation[Throwable, GroupCreatePayloadADM] =
          Validation.validateWith(id, name, descriptions, project, status, selfjoin)(GroupCreatePayloadADM)

        val requestMessage = for {
          payload        <- toFuture(validatedGroupCreatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield GroupCreateRequestADM(
          createRequest = payload,
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
   * Updates basic group information.
   */
  private def updateGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) { value =>
    put {
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

        val name: Validation[Throwable, Option[GroupName]] = GroupName.make(apiRequest.name)
        val descriptions: Validation[Throwable, Option[GroupDescriptions]] =
          GroupDescriptions.make(apiRequest.descriptions)
        val status: Validation[Throwable, Option[GroupStatus]]     = GroupStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, Option[GroupSelfJoin]] = GroupSelfJoin.make(apiRequest.selfjoin)

        val validatedGroupUpdatePayload: Validation[Throwable, GroupUpdatePayloadADM] =
          Validation.validateWith(name, descriptions, status, selfjoin)(GroupUpdatePayloadADM)

        val requestMessage = for {
          payload        <- toFuture(validatedGroupUpdatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield GroupChangeRequestADM(
          groupIri = checkedGroupIri,
          changeGroupRequest = payload,
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
   * Updates the group's status.
   */
  private def changeGroupStatus(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(GroupsBasePath / Segment / "status") { value =>
      put {
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
   * Deletes a group (sets status to false).
   */
  private def deleteGroup(featureFactoryConfig: FeatureFactoryConfig): Route = path(GroupsBasePath / Segment) { value =>
    delete { requestContext =>
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
}
