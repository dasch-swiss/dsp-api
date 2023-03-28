/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio._
import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.valueobjects.Group._
import dsp.valueobjects.Iri._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM._

/**
 * Provides a routing function for API routes that deal with groups.
 */

final case class GroupsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with GroupsADMJsonProtocol {

  private val groupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")

  override def makeRoute: Route =
    getGroups() ~
      getGroup() ~
      getGroupMembers() ~
      createGroup() ~
      updateGroup() ~
      changeGroupStatus() ~
      deleteGroup()

  /**
   * Returns all groups.
   */
  private def getGroups(): Route = path(groupsBasePath) {
    get(runJsonRoute(GroupsGetRequestADM(), _))
  }

  /**
   * Returns a single group identified by IRI.
   */
  private def getGroup(): Route = path(groupsBasePath / Segment) { groupIri =>
    get(runJsonRouteZ(validateAndEscape(groupIri).map(GroupGetRequestADM), _))
  }

  /**
   * Returns all members of single group.
   */
  private def getGroupMembers(): Route =
    path(groupsBasePath / Segment / "members") { memberIri =>
      get(ctx => runJsonRouteZ(getIriUser(memberIri, ctx).map(r => GroupMembersGetRequestADM(r.iri, r.user)), ctx))
    }

  /**
   * Creates a group.
   */
  private def createGroup(): Route = path(groupsBasePath) {
    post {
      entity(as[CreateGroupApiRequestADM]) { apiRequest => requestContext =>
        val id: Validation[Throwable, Option[GroupIri]]            = GroupIri.make(apiRequest.id)
        val name: Validation[Throwable, GroupName]                 = GroupName.make(apiRequest.name)
        val descriptions: Validation[Throwable, GroupDescriptions] = GroupDescriptions.make(apiRequest.descriptions)
        val project: Validation[Throwable, ProjectIri]             = ProjectIri.make(apiRequest.project)
        val status: Validation[Throwable, GroupStatus]             = GroupStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, GroupSelfJoin]         = GroupSelfJoin.make(apiRequest.selfjoin)
        val payloadValidation: Validation[Throwable, GroupCreatePayloadADM] =
          Validation.validateWith(id, name, descriptions, project, status, selfjoin)(GroupCreatePayloadADM)

        val requestTask = for {
          payload        <- payloadValidation.toZIO
          requestingUser <- Authenticator.getUserADM(requestContext)
          uuid           <- getApiRequestId
        } yield GroupCreateRequestADM(payload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Updates basic group information.
   */
  private def updateGroup(): Route = path(groupsBasePath / Segment) { value =>
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

        val requestTask = for {
          payload        <- validatedGroupUpdatePayload.toZIO
          requestingUser <- Authenticator.getUserADM(requestContext)
          uuid           <- getApiRequestId
        } yield GroupChangeRequestADM(checkedGroupIri, payload, requestingUser, uuid)
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Updates the group's status.
   */
  private def changeGroupStatus(): Route =
    path(groupsBasePath / Segment / "status") { value =>
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

          val requestTask = for {
            requestingUser <- Authenticator.getUserADM(requestContext)
            uuid           <- getApiRequestId
          } yield GroupChangeStatusRequestADM(checkedGroupIri, apiRequest, requestingUser, uuid)
          runJsonRouteZ(requestTask, requestContext)
        }
      }
    }

  /**
   * Deletes a group (sets status to false).
   */
  private def deleteGroup(): Route = path(groupsBasePath / Segment) { groupIri =>
    delete { ctx =>
      val task = for {
        r           <- getIriUserUuid(groupIri, ctx)
        changeStatus = ChangeGroupApiRequestADM(status = Some(false))
      } yield GroupChangeStatusRequestADM(r.iri, changeStatus, r.user, r.uuid)
      runJsonRouteZ(task, ctx)
    }
  }
}
